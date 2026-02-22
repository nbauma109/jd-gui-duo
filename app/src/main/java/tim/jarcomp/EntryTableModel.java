package tim.jarcomp;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import tim.jarcomp.EntryDetails.EntryStatus;

/**
 * Class to hold the table model for the comparison table
 */
public class EntryTableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;

    /** list of entries */
    private transient List<EntryDetails> entries;

    /**
     * Clear list to start a new comparison
     */
    public void reset() {
        entries = new ArrayList<>();
    }

    /**
     * Reset the table with the given list
     *
     * @param inList list of EntryDetails objects
     */
    public void setEntryList(List<EntryDetails> inList) {
        entries = inList;
        fireTableDataChanged();
    }

    /**
     * @return number of columns in table
     */
    @Override
    public int getColumnCount() {
        return 3;
        // TODO: Columns for size1, size2, status (as icon), size difference
    }

    /**
     * @return class of column, needed for sorting the Longs properly
     */
    @Override
    public Class<?> getColumnClass(int inColNum) {
        return switch (inColNum) {
            case 0, 1 -> String.class;
            case 2 -> SizeChange.class;
            default -> throw new IllegalArgumentException("Parameter out of range for getColumnClass(...) " + inColNum);
        };
    }

    /**
     * @return column name
     */
    @Override
    public String getColumnName(int inColNum) {
        return switch (inColNum) {
            case 0 -> "Filename";
            case 1 -> "Status";
            case 2 -> "Size Change";
            default -> throw new IllegalArgumentException("Parameter out of range for getColumnName(...) " + inColNum);
        };
    }

    /**
     * @return number of rows in the table
     */
    @Override
    public int getRowCount() {
        if (entries == null) {
            return 0;
        }
        return entries.size();
    }

    /**
     * @return object at specified row and column
     */
    @Override
    public Object getValueAt(int inRowNum, int inColNum) {
        if (inRowNum >= 0 && inRowNum < getRowCount()) {
            EntryDetails entry = entries.get(inRowNum);
            return switch (inColNum) {
                case 0 -> entry.getName();
                case 1 -> getText(entry.getStatus());
                case 2 -> entry.getSizeChange();
                default -> throw new IllegalArgumentException("Parameter out of range for getValueAt(...) " + inColNum);
            };
        }
        return null;
    }

    /**
     * Convert an entry status into text
     *
     * @param inStatus entry status
     * @return displayable text
     */
    private static String getText(EntryStatus inStatus) {
        return switch (inStatus) {
            case ADDED -> "Added";
            case CHANGED_SIZE -> "Changed size";
            case CHANGED_SUM -> "Changed sum";
            case EQUAL -> "=";
            case REMOVED -> "Removed";
            case SAME_SIZE -> "Same size";
            default -> inStatus.toString();
        };
    }

    /**
     * @return true if specified row represents a difference between the two files
     */
    public boolean areDifferent(int inRowNum) {
        if (inRowNum >= 0 && inRowNum < getRowCount()) {
            return entries.get(inRowNum).isChanged();
        }
        return false;
    }

    /**
     * @return true if specified row represents a difference that is not addition/deletion
     */
    public boolean isModification(int inRowNum) {
        if (inRowNum >= 0 && inRowNum < getRowCount()) {
            return switch (entries.get(inRowNum).getStatus()) {
                case ADDED, REMOVED, EQUAL, SAME_SIZE -> false;
                case CHANGED_SIZE, CHANGED_SUM -> true;
                default -> throw new IllegalArgumentException("Unknown status");
            };
        }
        return false;
    }
}
