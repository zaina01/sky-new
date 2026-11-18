package com.sky.job.plugin.script;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public final class ScriptOptionsValidation {
    private ScriptOptionsValidation() { }
    public static void validateInput(String input) {
        if(input==null||input.isBlank())return;
        try {var node=new ObjectMapper().readTree(input);if(node!=null&&node.isObject()&&node.isEmpty())return;}
        catch(Exception ignored) { }
        throw new IllegalArgumentException("Standalone scripts use environment and arguments, not JSON input");
    }
    public static void validateName(String name) {
        String upper=name.toUpperCase(Locale.ROOT);
        if(!name.matches("[A-Za-z_][A-Za-z0-9_]{0,127}")||upper.startsWith("SKY_")||upper.startsWith("NODE_")||upper.startsWith("PYTHON")||upper.startsWith("LD_")||upper.startsWith("DYLD_")||Set.of("PATH","PATHEXT","SYSTEMROOT","WINDIR","COMSPEC","HOME","USERPROFILE","TEMP","TMP","TMPDIR","LANG","LC_ALL","SSL_CERT_FILE","SSL_CERT_DIR","BASH_ENV","ENV").contains(upper))throw new IllegalArgumentException("Invalid or reserved script environment name: "+name);
    }
}
