package com.rodrilang.librarymanager.media.download;

public interface RemoteImageDownloader {

    boolean supports(String sourceUrl);

    DownloadedImage download(String sourceUrl);
}