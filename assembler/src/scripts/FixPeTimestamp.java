import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;

/**
 * Makes a Windows PE executable reproducible by neutralising all embedded
 * build-time timestamps and the checksum that reflects them:
 *
 *  1. COFF header TimeDateStamp         (pe+8)       – set to fixed epoch
 *  2. Optional header CheckSum          (optHdr+64)  – zeroed (not validated
 *                                                       by Windows for user apps)
 *  3. Every IMAGE_RESOURCE_DIRECTORY
 *     TimeDateStamp in the .rsrc section             – zeroed
 *     (windres sets each one to the current second)
 *
 * Usage: java FixPeTimestamp.java &lt;exe-path&gt; &lt;unix-epoch-seconds&gt;
 */
class FixPeTimestamp {
    public static void main(String[] args) throws Exception {
        var path = Paths.get(args[0]);
        // Accept both signed int and full 32-bit unsigned epoch values.
        int fixedTs = (int) Long.parseLong(args[1]);

        byte[] b = Files.readAllBytes(path);
        var buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);

        // e_lfanew at 0x3C: file offset of "PE\0\0"
        int pe      = buf.getInt(0x3c);
        int coff    = pe + 4;          // COFF header starts after the 4-byte PE signature
        int optHdr  = coff + 20;       // Optional header follows the 20-byte COFF header

        // 1. Fix COFF TimeDateStamp (Machine=2, NumberOfSections=2, then TimeDateStamp=4)
        buf.putInt(coff + 4, fixedTs);

        // 2. Zero the Optional Header CheckSum (Magic=2, Linker=2, SizeOfCode=4,
        //    InitData=4, UninitData=4, EntryPoint=4, BaseOfCode=4, BaseOfData=4,
        //    ImageBase=4, SecAlign=4, FileAlign=4, OSVer=4, ImgVer=4, SubsysVer=4,
        //    Win32Ver=4, SizeOfImage=4, SizeOfHeaders=4  → CheckSum at +64)
        buf.putInt(optHdr + 64, 0);

        // 3. Zero every IMAGE_RESOURCE_DIRECTORY.TimeDateStamp in .rsrc
        int numSections = buf.getShort(coff + 2) & 0xffff;
        int sizeOptional = buf.getShort(coff + 16) & 0xffff;
        int sectionsBase = optHdr + sizeOptional;

        for (int i = 0; i < numSections; i++) {
            int s = sectionsBase + i * 40;
            // Compare name bytes directly to avoid encoding issues
            if (b[s] == '.' && b[s+1] == 'r' && b[s+2] == 's' && b[s+3] == 'r' && b[s+4] == 'c') {
                int rsrcRaw = buf.getInt(s + 20); // PointerToRawData

                // BFS over the resource directory tree.
                // All offsets inside IMAGE_RESOURCE_DIRECTORY_ENTRY are relative to
                // the start of the .rsrc section (i.e. rsrcRaw).
                var queue = new ArrayDeque<Integer>();
                queue.add(0); // root directory is at section offset 0

                while (!queue.isEmpty()) {
                    int dirOff = rsrcRaw + queue.poll();

                    // Zero TimeDateStamp at directory offset +4
                    buf.putInt(dirOff + 4, 0);

                    int numNamed = buf.getShort(dirOff + 12) & 0xffff;
                    int numId    = buf.getShort(dirOff + 14) & 0xffff;

                    for (int e = 0; e < numNamed + numId; e++) {
                        int entryOff   = dirOff + 16 + e * 8;
                        int dataOffset = buf.getInt(entryOff + 4);
                        if ((dataOffset & 0x80000000) != 0) {
                            // High bit set → points to a subdirectory
                            queue.add(dataOffset & 0x7fffffff);
                        }
                        // High bit clear → IMAGE_RESOURCE_DATA_ENTRY (leaf), no timestamp
                    }
                }
                break;
            }
        }

        Files.write(path, b);
    }
}
