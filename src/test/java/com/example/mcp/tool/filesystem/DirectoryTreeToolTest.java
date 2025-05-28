package com.example.mcp.tool.filesystem;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.resource.ResourceLimiter;
import com.example.mcp.security.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DirectoryTreeToolTest {

    @Mock
    private SecurityContext mockSecurityContext;

    @Mock
    private ResourceLimiter mockResourceLimiter;

    @Mock
    private ResourceLimiter.ResourcePermit mockResourcePermit;

    private DirectoryTreeTool directoryTreeTool;

    @TempDir
    Path tempDir; // JUnit 5 temporary directory

    @BeforeEach
    void setUp() throws ToolExecutionException {
        // Configure default behavior for mocks
        when(mockSecurityContext.validatePath(anyString())).thenAnswer(invocation -> Path.of(invocation.getArgument(0)));
        when(mockResourceLimiter.acquireFileOperation(anyString())).thenReturn(mockResourcePermit);

        directoryTreeTool = new DirectoryTreeTool(mockSecurityContext, mockResourceLimiter);
    }

    private Map<String, Object> createArgs(String path) {
        return Map.of("path", path);
    }

    private Map<String, Object> createArgs(String path, int maxDepth) {
        return Map.of("path", path, "max_depth", maxDepth);
    }

    private Map<String, Object> createArgs(String path, int maxDepth, boolean showHidden, boolean includeFiles, List<String> ignorePatterns) {
        return Map.of(
                "path", path,
                "max_depth", maxDepth,
                "show_hidden", showHidden,
                "include_files", includeFiles,
                "ignore_patterns", ignorePatterns
        );
    }

    @Test
    void testToolInstantiation() {
        assertNotNull(directoryTreeTool);
        assertEquals("directory_tree", directoryTreeTool.getName());
        assertNotNull(directoryTreeTool.getDescription());
        assertNotNull(directoryTreeTool.getInputSchema());
    }

    @Test
    void testBasicDirectoryStructure() throws IOException, ToolExecutionException {
        // Create a simple directory structure
        Path dir1 = Files.createDirectory(tempDir.resolve("dir1"));
        Files.createFile(dir1.resolve("file1.txt"));
        Path dir2 = Files.createDirectory(tempDir.resolve("dir2"));
        Files.createFile(dir2.resolve("file2.txt"));
        Files.createFile(tempDir.resolve("rootfile.txt"));

        when(mockSecurityContext.validatePath(tempDir.toString())).thenReturn(tempDir);

        Map<String, Object> args = createArgs(tempDir.toString());
        McpModels.CallToolResponse.CallToolResult result = directoryTreeTool.execute(args);

        assertNotNull(result);
        assertFalse(result.isError);
        String output = result.content.get(0).text;

        assertTrue(output.contains("Directory Tree: " + tempDir.toAbsolutePath()));
        assertTrue(output.contains("📁 dir1/"));
        assertTrue(output.contains("📄 file1.txt"));
        assertTrue(output.contains("📁 dir2/"));
        assertTrue(output.contains("📄 file2.txt"));
        assertTrue(output.contains("📄 rootfile.txt"));
        assertTrue(output.contains("Summary: 2 directories, 3 files"));

        verify(mockSecurityContext).validatePath(tempDir.toString());
        verify(mockResourceLimiter).acquireFileOperation("directory_listing");
        verify(mockResourcePermit).close(); // Check that permit is closed
    }

    @Test
    void testMaxDepth() throws IOException, ToolExecutionException {
        Path dir1 = Files.createDirectory(tempDir.resolve("dir1"));
        Path dir1_1 = Files.createDirectory(dir1.resolve("dir1_1"));
        Files.createFile(dir1_1.resolve("file1_1.txt"));
        Files.createFile(dir1.resolve("file1.txt"));

        when(mockSecurityContext.validatePath(tempDir.toString())).thenReturn(tempDir);

        Map<String, Object> args = createArgs(tempDir.toString(), 1); // Max depth 1
        McpModels.CallToolResponse.CallToolResult result = directoryTreeTool.execute(args);
        String output = result.content.get(0).text;

        assertTrue(output.contains("📁 dir1/"));
        assertTrue(output.contains("📄 file1.txt")); // file1.txt is at depth 1 relative to dir1 content, or depth 2 from root.
                                                    // DirectoryTreeTool's depth for files is parent_dir_depth + 1
                                                    // So if dir1 is depth 1, file1.txt is depth 1.
        assertFalse(output.contains("📁 dir1_1/"));
        assertFalse(output.contains("📄 file1_1.txt"));
        assertTrue(output.contains("Summary: 1 directories, 1 files")); // dir1 and its direct file file1.txt
    }


    @Test
    void testShowHidden_False() throws IOException, ToolExecutionException {
        Files.createDirectory(tempDir.resolve(".hiddenDir"));
        Files.createFile(tempDir.resolve(".hiddenFile.txt"));
        Files.createFile(tempDir.resolve("visibleFile.txt"));

        when(mockSecurityContext.validatePath(tempDir.toString())).thenReturn(tempDir);

        Map<String, Object> args = createArgs(tempDir.toString(), 5, false, true, Collections.emptyList());
        McpModels.CallToolResponse.CallToolResult result = directoryTreeTool.execute(args);
        String output = result.content.get(0).text;

        assertFalse(output.contains("📁 .hiddenDir/"));
        assertFalse(output.contains("📄 .hiddenFile.txt"));
        assertTrue(output.contains("📄 visibleFile.txt"));
        assertTrue(output.contains("Summary: 0 directories, 1 files (2 ignored)"));
    }

    @Test
    void testShowHidden_True() throws IOException, ToolExecutionException {
        Files.createDirectory(tempDir.resolve(".hiddenDir"));
        Files.createFile(tempDir.resolve(".hiddenDir/.hiddenFileInside.txt"));
        Files.createFile(tempDir.resolve("visibleFile.txt"));

        when(mockSecurityContext.validatePath(tempDir.toString())).thenReturn(tempDir);
        // Need to mock validatePath for subdirectories if they are accessed explicitly by the tool,
        // but walkFileTree handles traversal.
        // For this test, we assume the structure is valid once the root is.

        Map<String, Object> args = createArgs(tempDir.toString(), 5, true, true, Collections.emptyList());
        McpModels.CallToolResponse.CallToolResult result = directoryTreeTool.execute(args);
        String output = result.content.get(0).text;

        assertTrue(output.contains("📁 .hiddenDir/"));
        assertTrue(output.contains("📄 .hiddenFileInside.txt"));
        assertTrue(output.contains("📄 visibleFile.txt"));
        assertTrue(output.contains("Summary: 1 directories, 2 files"));
    }


    @Test
    void testIncludeFiles_False() throws IOException, ToolExecutionException {
        Path dir1 = Files.createDirectory(tempDir.resolve("dir1"));
        Files.createFile(dir1.resolve("file1.txt"));
        Files.createFile(tempDir.resolve("rootfile.txt"));

        when(mockSecurityContext.validatePath(tempDir.toString())).thenReturn(tempDir);

        Map<String, Object> args = createArgs(tempDir.toString(), 5, false, false, Collections.emptyList());
        McpModels.CallToolResponse.CallToolResult result = directoryTreeTool.execute(args);
        String output = result.content.get(0).text;

        assertTrue(output.contains("📁 dir1/"));
        assertFalse(output.contains("📄 file1.txt"));
        assertFalse(output.contains("📄 rootfile.txt"));
        assertTrue(output.contains("Summary: 1 directories, 0 files"));
    }

    @Test
    void testIgnorePatterns_Default() throws IOException, ToolExecutionException {
        Files.createDirectory(tempDir.resolve(".git"));
        Files.createFile(tempDir.resolve(".git/config"));
        Files.createDirectory(tempDir.resolve("node_modules"));
        Files.createFile(tempDir.resolve("node_modules/somepackage.js"));
        Files.createFile(tempDir.resolve("visible.txt"));

        when(mockSecurityContext.validatePath(tempDir.toString())).thenReturn(tempDir);

        Map<String, Object> args = createArgs(tempDir.toString(), 5, false, true, Collections.emptyList());
        McpModels.CallToolResponse.CallToolResult result = directoryTreeTool.execute(args);
        String output = result.content.get(0).text;

        assertFalse(output.contains("📁 .git/"));
        assertFalse(output.contains("📁 node_modules/"));
        assertTrue(output.contains("📄 visible.txt"));
        assertTrue(output.contains("Summary: 0 directories, 1 files (2 ignored)"));
    }

    @Test
    void testIgnorePatterns_Custom() throws IOException, ToolExecutionException {
        Files.createDirectory(tempDir.resolve("logs"));
        Files.createFile(tempDir.resolve("logs/app.log"));
        Files.createDirectory(tempDir.resolve("output"));
        Files.createFile(tempDir.resolve("output/data.out"));
        Files.createFile(tempDir.resolve("important.dat"));


        when(mockSecurityContext.validatePath(tempDir.toString())).thenReturn(tempDir);

        Map<String, Object> args = createArgs(tempDir.toString(), 5, false, true, List.of("logs", "*.out"));
        McpModels.CallToolResponse.CallToolResult result = directoryTreeTool.execute(args);
        String output = result.content.get(0).text;

        assertFalse(output.contains("📁 logs/"));
        assertFalse(output.contains("📄 app.log"));
        assertTrue(output.contains("📁 output/")); // output directory itself is not ignored
        assertFalse(output.contains("📄 data.out")); // but its content matching *.out is
        assertTrue(output.contains("📄 important.dat"));
        // output/ directory itself is listed, logs/ directory is ignored. data.out is ignored.
        // So, 1 directory (output), 1 file (important.dat).
        // Ignored: logs dir (and its content implicitly), data.out file.
        // The count for ignored items by TreeWalker might be tricky: if "logs" dir is skipped, its contents aren't visited.
        // If a pattern like "*.out" matches a file, that file is ignored.
        // Here, "logs" is one ignored item (subtree). "*.out" (data.out) is another. So 2 ignored.
        assertTrue(output.contains("Summary: 1 directories, 1 files (2 ignored)"));
    }


    @Test
    void testInvalidPath_NonExistent() {
        String nonExistentPath = tempDir.resolve("nonexistent").toString();
        when(mockSecurityContext.validatePath(nonExistentPath))
                .thenThrow(new ToolExecutionException("Path does not resolve or access denied: " + nonExistentPath));

        Map<String, Object> args = createArgs(nonExistentPath);
        ToolExecutionException exception = assertThrows(ToolExecutionException.class, () -> {
            directoryTreeTool.execute(args);
        });
        assertTrue(exception.getMessage().contains("Path does not resolve or access denied: " + nonExistentPath));
        // Verify that validatePath was called (it's part of the BaseMcpTool.execute flow)
        verify(mockSecurityContext).validatePath(nonExistentPath);
    }

    @Test
    void testInvalidPath_IsFile() throws IOException {
        Path filePath = Files.createFile(tempDir.resolve("aFile.txt"));
        when(mockSecurityContext.validatePath(filePath.toString())).thenReturn(filePath); // Assume path itself is valid by security context

        Map<String, Object> args = createArgs(filePath.toString());

        // The check for isDirectory is in DirectoryTreeTool.validateInputs
        ToolExecutionException exception = assertThrows(ToolExecutionException.class, () -> {
            directoryTreeTool.execute(args);
        });

        assertTrue(exception.getMessage().contains("Path is not a directory: " + filePath));
        verify(mockSecurityContext).validatePath(filePath.toString());
    }


    @Test
    void testInvalidMaxDepth_TooLow() {
        Map<String, Object> args = createArgs(tempDir.toString(), 0); // Invalid depth
        ToolExecutionException exception = assertThrows(ToolExecutionException.class, () -> {
            directoryTreeTool.execute(args);
        });
        assertEquals("max_depth must be between 1 and 20.", exception.getMessage());
    }

    @Test
    void testInvalidMaxDepth_TooHigh() {
        Map<String, Object> args = createArgs(tempDir.toString(), 21); // Invalid depth
        ToolExecutionException exception = assertThrows(ToolExecutionException.class, () -> {
            directoryTreeTool.execute(args);
        });
        assertEquals("max_depth must be between 1 and 20.", exception.getMessage());
    }

    @Test
    void testSecurityContext_ValidatePathCalled() throws ToolExecutionException {
        Files.createFile(tempDir.resolve("dummy.txt")); // ensure tempDir exists for the call
        when(mockSecurityContext.validatePath(tempDir.toString())).thenReturn(tempDir);

        directoryTreeTool.execute(createArgs(tempDir.toString()));
        // BaseMcpTool.execute calls validateInputs, which calls this.validatePath()
        // DirectoryTreeTool.validateInputs calls this.validatePath() which is from BaseMcpTool
        // and BaseMcpTool.validatePath calls securityContext.validatePath()
        verify(mockSecurityContext, times(1)).validatePath(tempDir.toString());
    }


    @Test
    void testResourceLimiter_AcquireFileOperationCalled() throws ToolExecutionException {
         Files.createFile(tempDir.resolve("dummy.txt"));
         when(mockSecurityContext.validatePath(tempDir.toString())).thenReturn(tempDir);

        directoryTreeTool.execute(createArgs(tempDir.toString()));
        verify(mockResourceLimiter).acquireFileOperation("directory_listing");
        verify(mockResourcePermit).close(); // Ensure permit is closed
    }

    // Test for Files.walkFileTree throwing IOException (simulated)
    // This is more complex as Files.walkFileTree is a static method.
    // A more direct way to test this part of DirectoryTreeTool's error handling
    // would be to refactor TreeWalker to be injectable or make the walkFileTree call injectable,
    // but that's beyond the scope of this test.
    // For now, we assume that if Files.walkFileTree *did* throw an IOException (e.g. permissions),
    // it would be caught by the catch (IOException e) block in executeInternal.

    // A simple test for empty directory
    @Test
    void testEmptyDirectory() throws ToolExecutionException {
        when(mockSecurityContext.validatePath(tempDir.toString())).thenReturn(tempDir);
        Map<String, Object> args = createArgs(tempDir.toString());
        McpModels.CallToolResponse.CallToolResult result = directoryTreeTool.execute(args);
        String output = result.content.get(0).text;

        assertTrue(output.contains("Directory Tree: " + tempDir.toAbsolutePath()));
        assertTrue(output.contains("Summary: 0 directories, 0 files")); // No content other than root
    }
}
