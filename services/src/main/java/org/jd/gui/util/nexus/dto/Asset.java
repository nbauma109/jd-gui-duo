/*******************************************************************************
 * Copyright (C) 2008-2025 Emmanuel Dupuy and other contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.jd.gui.util.nexus.dto;

/**
 * We represent an individual asset (file) belonging to a component.
 */
public final class Asset {

    private String downloadUrl;
    private String path;
    private String id;
    private String repository;
    private String format;
    private Checksum checksum;
    private String contentType;
    private String lastModified;
    private String lastDownloaded;
    private String uploader;
    private String uploaderIp;
    private Long fileSize;
    private String blobCreated;
    private Maven2 maven2;

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Checksum getChecksum() {
        return checksum;
    }

    public void setChecksum(Checksum checksum) {
        this.checksum = checksum;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getLastModified() {
        return lastModified;
    }

    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }

    public String getLastDownloaded() {
        return lastDownloaded;
    }

    public void setLastDownloaded(String lastDownloaded) {
        this.lastDownloaded = lastDownloaded;
    }

    public String getUploader() {
        return uploader;
    }

    public void setUploader(String uploader) {
        this.uploader = uploader;
    }

    public String getUploaderIp() {
        return uploaderIp;
    }

    public void setUploaderIp(String uploaderIp) {
        this.uploaderIp = uploaderIp;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getBlobCreated() {
        return blobCreated;
    }

    public void setBlobCreated(String blobCreated) {
        this.blobCreated = blobCreated;
    }

    public Maven2 getMaven2() {
        return maven2;
    }

    public void setMaven2(Maven2 maven2) {
        this.maven2 = maven2;
    }
}