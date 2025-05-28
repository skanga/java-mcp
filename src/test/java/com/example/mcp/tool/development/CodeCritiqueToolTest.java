package com.example.mcp.tool.development;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.resource.ResourceLimiter;
import com.example.mcp.security.SecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CodeCritiqueToolTest {

    @Mock
    private SecurityContext mockSecurityContext;
    @Mock
    private ResourceLimiter mockResourceLimiter;
    @Mock
    private ResourceLimiter.ResourcePermit mockResourcePermit;
    @Mock
    private CodeCritiqueTool.CodeAnalyzer mockAnalyzer; 

    @Spy
    private CodeCritiqueTool toolSpy; 

    @TempDir
    Path tempDir;
    Path mockFilePath;
    Path mockFilePath2;

    private static final int MAX_FILE_SIZE = 1024 * 1024;

    @BeforeEach
    void setUp() throws IOException, ToolExecutionException {
        mockFilePath = tempDir.resolve("testfile.java");
        Files.writeString(mockFilePath, "public class Test {}"); // Default content
        mockFilePath2 = tempDir.resolve("testfile2.java");
        Files.writeString(mockFilePath2, "public class AnotherTest {}"); // Default content

        when(mockSecurityContext.validatePath(anyString())).thenAnswer(invocation -> {
            String pathStr = invocation.getArgument(0);
            if (mockFilePath.toString().equals(pathStr)) return mockFilePath;
            if (mockFilePath2.toString().equals(pathStr)) return mockFilePath2;
            return tempDir.resolve(pathStr);
        });
        when(mockSecurityContext.validatePath(mockFilePath.toString())).thenReturn(mockFilePath);
        when(mockSecurityContext.validatePath(mockFilePath2.toString())).thenReturn(mockFilePath2);

        doNothing().when(mockSecurityContext).validateFileSize(any(Path.class));
        when(mockResourceLimiter.acquireFileOperation("code_analysis")).thenReturn(mockResourcePermit);

        CodeCritiqueTool realTool = new CodeCritiqueTool(mockSecurityContext, mockResourceLimiter);
        toolSpy = spy(realTool);

        doReturn(mockAnalyzer).when(toolSpy).getAnalyzer(anyString());
        
        when(mockAnalyzer.calculateMetrics(anyString())).thenReturn(Collections.emptyMap());
        when(mockAnalyzer.findIssues(anyString())).thenReturn(Collections.emptyList());
        when(mockAnalyzer.findSecurityIssues(anyString())).thenReturn(Collections.emptyList());
        when(mockAnalyzer.findPerformanceIssues(anyString())).thenReturn(Collections.emptyList());
        when(mockAnalyzer.checkBestPractices(anyString())).thenReturn(Collections.emptyList());
    }

    private Map<String, Object> createArgs(String action, Map<String, Object> params) {
        Map<String, Object> args = new java.util.HashMap<>(params);
        args.put("action", action);
        return args;
    }
    
    private String generateStringOfSize(int size) {
        return "a".repeat(Math.max(0, size));
    }

    @Test
    void testToolInstantiationAndBasicInfo() {
        assertNotNull(toolSpy);
        assertEquals("code_critique", toolSpy.getName());
        assertNotNull(toolSpy.getDescription());
        assertTrue(toolSpy.getInputSchema().containsKey("type"));
        verify(mockResourcePermit, never()).close();
    }

    // --- validateInputs Tests ---
    @Test
    void testValidateInputs_analyzeFile_valid() {
        Map<String, Object> args = createArgs("analyze_file", Map.of("file_path", mockFilePath.toString()));
        assertDoesNotThrow(() -> toolSpy.validateInputs(args));
        verify(mockSecurityContext).validatePath(mockFilePath.toString());
    }

    @Test
    void testValidateInputs_analyzeFile_missingFilePath() {
        Map<String, Object> args = createArgs("analyze_file", Collections.emptyMap());
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("File path is required for 'analyze_file' action.", ex.getMessage());
    }

    @Test
    void testValidateInputs_analyzeFile_notRegularFile() throws IOException {
        Path dirPath = tempDir.resolve("testdir");
        Files.createDirectory(dirPath);
        when(mockSecurityContext.validatePath(dirPath.toString())).thenReturn(dirPath);
        Map<String, Object> args = createArgs("analyze_file", Map.of("file_path", dirPath.toString()));
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertTrue(ex.getMessage().contains("Path is not a regular file:"));
    }

    @Test
    void testValidateInputs_analyzeCode_valid() {
        Map<String, Object> args = createArgs("analyze_code", Map.of("code", "public class Test {}"));
        assertDoesNotThrow(() -> toolSpy.validateInputs(args));
    }

    @Test
    void testValidateInputs_analyzeCode_missingCode() {
        Map<String, Object> args = createArgs("analyze_code", Collections.emptyMap());
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("Code content is required for 'analyze_code' action.", ex.getMessage());
    }
    
    @Test
    void testValidateInputs_analyzeCode_tooLarge() {
        String largeCode = generateStringOfSize(MAX_FILE_SIZE + 1);
        Map<String, Object> args = createArgs("analyze_code", Map.of("code", largeCode));
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("Code too large for analysis (max 1MB).", ex.getMessage());
    }

    @Test
    void testValidateInputs_compareFiles_valid() {
        Map<String, Object> args = createArgs("compare_files", Map.of(
            "file_path", mockFilePath.toString(),
            "file_path_2", mockFilePath2.toString()
        ));
        assertDoesNotThrow(() -> toolSpy.validateInputs(args));
        verify(mockSecurityContext).validatePath(mockFilePath.toString());
        verify(mockSecurityContext).validatePath(mockFilePath2.toString());
    }

    @Test
    void testValidateInputs_compareFiles_missingPath1() {
        Map<String, Object> args = createArgs("compare_files", Map.of("file_path_2", mockFilePath2.toString()));
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("Both file_path and file_path_2 are required for 'compare_files' action.", ex.getMessage());
    }
    
    @Test
    void testValidateInputs_suggestImprovements_withFile_valid() {
        Map<String, Object> args = createArgs("suggest_improvements", Map.of("file_path", mockFilePath.toString()));
        assertDoesNotThrow(() -> toolSpy.validateInputs(args));
         verify(mockSecurityContext).validatePath(mockFilePath.toString());
    }

    @Test
    void testValidateInputs_suggestImprovements_withCode_valid() {
        Map<String, Object> args = createArgs("suggest_improvements", Map.of("code", "sample code"));
        assertDoesNotThrow(() -> toolSpy.validateInputs(args));
    }
    
    @Test
    void testValidateInputs_suggestImprovements_missingFileAndCode() {
        Map<String, Object> args = createArgs("suggest_improvements", Collections.emptyMap());
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("Either file_path or code must be provided for 'suggest_improvements' action.", ex.getMessage());
    }
    
    @Test
    void testValidateInputs_invalidAction() {
        Map<String, Object> args = createArgs("fly_me_to_the_moon", Collections.emptyMap());
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertTrue(ex.getMessage().contains("Invalid action: fly_me_to_the_moon"));
    }

    @Test
    void testValidateInputs_invalidLanguage() {
        Map<String, Object> args = createArgs("analyze_code", Map.of("code", "test", "language", "cobol"));
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertTrue(ex.getMessage().contains("Invalid language: cobol"));
    }

    @Test
    void testAcquireResourcesAndPermitClosureOnSuccess() throws ToolExecutionException, IOException {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.readString(any(Path.class))).thenReturn("public class Test {}");
            mockedFiles.when(() -> Files.size(any(Path.class))).thenReturn(10L); 

            Map<String, Object> args = createArgs("analyze_file", Map.of("file_path", mockFilePath.toString()));
            toolSpy.execute(args); 
            verify(mockResourceLimiter).acquireFileOperation("code_analysis");
            verify(mockResourcePermit).close();
        }
    }
    
    @Test
    void testSecurity_ValidatePathCalled_AnalyzeFile() {
        Map<String, Object> args = createArgs("analyze_file", Map.of("file_path", mockFilePath.toString()));
        assertDoesNotThrow(() -> toolSpy.validateInputs(args)); 
        verify(mockSecurityContext).validatePath(mockFilePath.toString());
    }
    
    @Test
    void testSecurity_ValidateFileSizeCalled_AnalyzeFile() throws Exception {
         try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.readString(any(Path.class))).thenReturn("public class Test {}");
            mockedFiles.when(() -> Files.size(any(Path.class))).thenReturn(10L);

            Map<String, Object> args = createArgs("analyze_file", Map.of("file_path", mockFilePath.toString()));
            toolSpy.execute(args);
            verify(mockSecurityContext).validateFileSize(mockFilePath); 
        }
    }

    @Test
    void testSecurity_ValidateFileSizeThrows_AnalyzeFile() throws Exception {
        doThrow(new ToolExecutionException("File too large per security policy!"))
            .when(mockSecurityContext).validateFileSize(mockFilePath);

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.size(mockFilePath)).thenReturn(10L); // Local size check passes

            Map<String, Object> args = createArgs("analyze_file", Map.of("file_path", mockFilePath.toString()));
            ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.execute(args));
            assertTrue(ex.getMessage().contains("File too large per security policy!"));
        }
    }
    
    @Test
    void testSecurity_LocalFileSizeCheck_AnalyzeFile_TooLarge() throws Exception {
         long largeSize = MAX_FILE_SIZE + 1;
         try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.size(mockFilePath)).thenReturn(largeSize);
            // validateFileSize from SecurityContext is not throwing, so local check should be hit.
            
            Map<String, Object> args = createArgs("analyze_file", Map.of("file_path", mockFilePath.toString()));
            ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.execute(args));
            assertTrue(ex.getMessage().contains("File too large for analysis (max " + MAX_FILE_SIZE + " bytes)"));
        }
    }

    @Test
    void testActionDispatch_analyzeFile() throws Exception {
        Map<String, Object> args = createArgs("analyze_file", Map.of("file_path", mockFilePath.toString()));
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.readString(mockFilePath)).thenReturn("class Test {}");
            mockedFiles.when(() -> Files.size(mockFilePath)).thenReturn(10L);
            toolSpy.execute(args);
            verify(toolSpy).getAnalyzer("java"); 
            verify(mockAnalyzer).calculateMetrics("class Test {}");
        }
    }

    @Test
    void testActionDispatch_analyzeCode() throws Exception {
        String testCode = "function main() {}";
        Map<String, Object> args = createArgs("analyze_code", Map.of("code", testCode, "language", "javascript"));
        toolSpy.execute(args);
        verify(toolSpy).getAnalyzer("javascript");
        verify(mockAnalyzer).calculateMetrics(testCode);
    }

    @Test
    void testActionDispatch_compareFiles() throws Exception {
        String code1 = "public class File1 {}";
        String code2 = "public class File2 {}";
        Map<String, Object> args = createArgs("compare_files", Map.of(
            "file_path", mockFilePath.toString(),
            "file_path_2", mockFilePath2.toString(),
            "language", "java" // Specify to simplify analyzer verification
        ));

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.readString(mockFilePath)).thenReturn(code1);
            mockedFiles.when(() -> Files.size(mockFilePath)).thenReturn((long)code1.length());
            mockedFiles.when(() -> Files.readString(mockFilePath2)).thenReturn(code2);
            mockedFiles.when(() -> Files.size(mockFilePath2)).thenReturn((long)code2.length());

            toolSpy.execute(args);
            verify(toolSpy, times(2)).getAnalyzer("java"); // Called for each file
            verify(mockAnalyzer).calculateMetrics(code1);
            verify(mockAnalyzer).calculateMetrics(code2);
        }
    }

    @Test
    void testActionDispatch_suggestImprovements_withFile() throws Exception {
        String fileCode = "public class MyClass { void oldMethod() {} }";
        Map<String, Object> args = createArgs("suggest_improvements", Map.of(
            "file_path", mockFilePath.toString(),
            "language", "java"
        ));
         try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.readString(mockFilePath)).thenReturn(fileCode);
            mockedFiles.when(() -> Files.size(mockFilePath)).thenReturn((long)fileCode.length());
            
            toolSpy.execute(args);
            verify(toolSpy).getAnalyzer("java");
            verify(mockAnalyzer).findIssues(fileCode); // suggestImprovements uses findIssues, etc.
        }
    }
    
    @Test
    void testActionDispatch_suggestImprovements_withCode() throws Exception {
        String directCode = "function oldJs() { return 1==1; }";
         Map<String, Object> args = createArgs("suggest_improvements", Map.of(
            "code", directCode,
            "language", "javascript"
        ));
        toolSpy.execute(args);
        verify(toolSpy).getAnalyzer("javascript");
        verify(mockAnalyzer).findIssues(directCode);
    }
    
    @Test
    void testLanguageDetection_Explicit() throws Exception {
        String testCode = "public class A {}";
        Map<String, Object> args = createArgs("analyze_code", Map.of("code", testCode, "language", "java"));
        toolSpy.execute(args);
        verify(toolSpy).getAnalyzer("java");
    }

    @Test
    void testLanguageDetection_FromFileExtension() throws Exception {
         try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.readString(mockFilePath)).thenReturn("class Test {}"); 
            mockedFiles.when(() -> Files.size(mockFilePath)).thenReturn(10L);

            Map<String, Object> args = createArgs("analyze_file", Map.of("file_path", mockFilePath.toString(), "language", "auto"));
            toolSpy.execute(args);
            verify(toolSpy).getAnalyzer("java");
        }
    }

    @Test
    void testLanguageDetection_Auto_NoFilePath_DefaultsToJava() throws Exception {
        Map<String, Object> args = createArgs("analyze_code", Map.of("code", "...", "language", "auto"));
        toolSpy.execute(args);
        verify(toolSpy).getAnalyzer("java"); 
    }

    @Test
    void testFormatAnalysisResult_Basic() throws Exception {
        when(mockAnalyzer.calculateMetrics(anyString())).thenReturn(Map.of("Complexity", 10));
        List<CodeCritiqueTool.CodeIssue> issues = List.of(new CodeCritiqueTool.CodeIssue("Bad practice", "medium"));
        when(mockAnalyzer.findIssues(anyString())).thenReturn(issues);

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.readString(mockFilePath)).thenReturn("code content");
            mockedFiles.when(() -> Files.size(mockFilePath)).thenReturn(10L);

            Map<String, Object> args = createArgs("analyze_file", Map.of("file_path", mockFilePath.toString(), "language", "java"));
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
            
            assertFalse(result.isError);
            String output = result.content.get(0).text;
            assertTrue(output.contains("🔍 Code Quality Analysis"));
            assertTrue(output.contains("Source: " + mockFilePath.toString()));
            assertTrue(output.contains("Language: JAVA"));
            assertTrue(output.contains("Quality Score:")); 
            assertTrue(output.contains("📊 Code Metrics"));
            assertTrue(output.contains("Complexity: 10"));
            assertTrue(output.contains("⚠️  Code Issues (1)"));
            assertTrue(output.contains("🟡 Bad practice"));
        }
    }

    @Test
    void testFormatComparison_OutputStructure() throws Exception {
        String code1 = "public class File1 {}";
        String code2 = "public class File2 {}"; // Assume File2 is slightly "better"
        
        // Mock analyzer for file1
        CodeCritiqueTool.CodeAnalyzer mockAnalyzer1 = mock(CodeCritiqueTool.CodeAnalyzer.class);
        when(mockAnalyzer1.calculateMetrics(anyString())).thenReturn(Map.of("Lines", 10));
        when(mockAnalyzer1.findIssues(anyString())).thenReturn(List.of(new CodeCritiqueTool.CodeIssue("Issue in file1", "low")));
        when(mockAnalyzer1.findSecurityIssues(anyString())).thenReturn(Collections.emptyList());
        when(mockAnalyzer1.findPerformanceIssues(anyString())).thenReturn(Collections.emptyList());
        when(mockAnalyzer1.checkBestPractices(anyString())).thenReturn(Collections.emptyList());


        // Mock analyzer for file2 (better quality)
        CodeCritiqueTool.CodeAnalyzer mockAnalyzer2 = mock(CodeCritiqueTool.CodeAnalyzer.class);
        when(mockAnalyzer2.calculateMetrics(anyString())).thenReturn(Map.of("Lines", 8));
        when(mockAnalyzer2.findIssues(anyString())).thenReturn(Collections.emptyList()); // Fewer issues
        when(mockAnalyzer2.findSecurityIssues(anyString())).thenReturn(Collections.emptyList());
        when(mockAnalyzer2.findPerformanceIssues(anyString())).thenReturn(Collections.emptyList());
        when(mockAnalyzer2.checkBestPractices(anyString())).thenReturn(List.of("Good practice"));


        // Stub getAnalyzer to return different analyzers based on code content (or path if distinguishable)
        // This is a bit tricky as getAnalyzer is based on language string.
        // For this test, we'll assume both files are "java" but force different AnalysisResult by controlling the mockAnalyzer for each call to analyzeCodeInternal.
        // This requires deeper stubbing of private methods, which is hard.
        // Alternative: The spy's getAnalyzer returns the same mockAnalyzer, but we change mockAnalyzer's behavior between calls.

        when(toolSpy.getAnalyzer("java")).thenReturn(mockAnalyzer); // Same mock analyzer for both

        // Behavior for file1
        when(mockAnalyzer.calculateMetrics(code1)).thenReturn(Map.of("Lines", 10));
        when(mockAnalyzer.findIssues(code1)).thenReturn(List.of(new CodeCritiqueTool.CodeIssue("Issue in file1", "low")));
         // ... other methods for file1 return empty lists

        // Behavior for file2
        when(mockAnalyzer.calculateMetrics(code2)).thenReturn(Map.of("Lines", 8));
        when(mockAnalyzer.findIssues(code2)).thenReturn(Collections.emptyList());
        when(mockAnalyzer.checkBestPractices(code2)).thenReturn(List.of("Good practice"));
        // ... other methods for file2 return empty lists


        Map<String, Object> args = createArgs("compare_files", Map.of(
            "file_path", mockFilePath.toString(),
            "file_path_2", mockFilePath2.toString(),
            "language", "java"
        ));

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.readString(mockFilePath)).thenReturn(code1);
            mockedFiles.when(() -> Files.size(mockFilePath)).thenReturn((long)code1.length());
            mockedFiles.when(() -> Files.readString(mockFilePath2)).thenReturn(code2);
            mockedFiles.when(() -> Files.size(mockFilePath2)).thenReturn((long)code2.length());

            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
            assertFalse(result.isError);
            String output = result.content.get(0).text;

            assertTrue(output.contains("🔍 Code Comparison Analysis"));
            assertTrue(output.contains("File 1: " + mockFilePath.toString()));
            assertTrue(output.contains("File 2: " + mockFilePath2.toString()));
            assertTrue(output.contains("Quality Score"));
            assertTrue(output.contains("Lines of Code"));
            // Based on our mocked behavior, file2 should be better (fewer issues, one good practice)
            assertTrue(output.contains("File 2 has better overall code quality"));
        }
    }

    @Test
    void testFormatImprovementSuggestions_OutputStructure() throws Exception {
        String code = "public class BadClass { String s = new String(\"foo\"); }"; // Example with issues
        List<CodeCritiqueTool.CodeIssue> issues = new ArrayList<>();
        issues.add(new CodeCritiqueTool.CodeIssue("Redundant String constructor", "medium", 1, "Use string literal \"foo\""));
        issues.add(new CodeCritiqueTool.CodeIssue("Security flaw X", "high", 2, "Sanitize input Y"));
        
        when(mockAnalyzer.findIssues(code)).thenReturn(List.of(issues.get(0)));
        when(mockAnalyzer.findSecurityIssues(code)).thenReturn(List.of(issues.get(1)));
        // other find methods return empty lists

        Map<String, Object> args = createArgs("suggest_improvements", Map.of(
            "code", code,
            "language", "java"
        ));
        
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
        assertFalse(result.isError);
        String output = result.content.get(0).text;

        assertTrue(output.contains("💡 Code Improvement Suggestions"));
        assertTrue(output.contains("Source: <direct_input>"));
        assertTrue(output.contains("🎯 Priority Improvements"));
        assertTrue(output.contains("1. 🔴 Security flaw X")); // High severity first
        assertTrue(output.contains("💡 Sanitize input Y"));
        assertTrue(output.contains("2. 🟡 Redundant String constructor"));
        assertTrue(output.contains("💡 Use string literal \"foo\""));
        assertTrue(output.contains("🚀 Quick Wins")); // May or may not list these exact ones depending on limits
    }
    
    @Test
    void testErrorHandling_IOException_AnalyzeFile() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.size(mockFilePath)).thenReturn(10L); 
            mockedFiles.when(() -> Files.readString(mockFilePath)).thenThrow(new IOException("Cannot read file!"));

            Map<String, Object> args = createArgs("analyze_file", Map.of("file_path", mockFilePath.toString()));
            ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.execute(args));
            assertTrue(ex.getMessage().contains("Code critique IO failed: Cannot read file!"));
        }
    }
}
