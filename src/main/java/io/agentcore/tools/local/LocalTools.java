package io.agentcore.tools.local;

import io.agentcore.tools.base.Tool;
import io.agentcore.tools.operations.BashOperations;
import io.agentcore.tools.operations.FileOperations;
import io.agentcore.tools.operations.LocalBashOperations;
import io.agentcore.tools.operations.LocalFileOperations;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory for creating local filesystem tools.
 */
public final class LocalTools {
    private LocalTools() {}

    public static Map<String, Tool> createAll(String cwd, FileOperations fileOps, BashOperations bashOps) {
        Map<String, Tool> tools = new LinkedHashMap<>();
        tools.put("read", new ReadTool(cwd, fileOps));
        tools.put("write", new WriteTool(cwd, fileOps));
        tools.put("edit", new EditTool(cwd, fileOps));
        tools.put("bash", new BashTool(cwd, bashOps));
        tools.put("grep", new GrepTool(cwd, fileOps));
        tools.put("find", new FindTool(cwd, fileOps));
        tools.put("ls", new LsTool(cwd, fileOps));
        tools.put("confirm", new ConfirmTool());
        return tools;
    }

    public static Map<String, Tool> createAll(String cwd) {
        return createAll(cwd, new LocalFileOperations(cwd), new LocalBashOperations(cwd, null));
    }

    public static Map<String, Tool> createCoding(String cwd, FileOperations fileOps, BashOperations bashOps) {
        Map<String, Tool> tools = new LinkedHashMap<>();
        tools.put("read", new ReadTool(cwd, fileOps));
        tools.put("bash", new BashTool(cwd, bashOps));
        tools.put("write", new WriteTool(cwd, fileOps));
        return tools;
    }

    public static Map<String, Tool> createReadOnly(String cwd, FileOperations fileOps) {
        Map<String, Tool> tools = new LinkedHashMap<>();
        tools.put("read", new ReadTool(cwd, fileOps));
        tools.put("grep", new GrepTool(cwd, fileOps));
        tools.put("find", new FindTool(cwd, fileOps));
        tools.put("ls", new LsTool(cwd, fileOps));
        return tools;
    }
}
