package com.example;

interface IFileService {
    List<String> listFiles(String dirPath);
    String readFile(String filePath);
    boolean writeFile(String filePath, String content);
    boolean createFolder(String parentPath, String folderName);
    boolean createFile(String parentPath, String fileName);
    boolean deletePath(String path);
    boolean renamePath(String oldPath, String newPath);
    boolean copyFile(String srcPath, String destPath);
    void destroy();
}
