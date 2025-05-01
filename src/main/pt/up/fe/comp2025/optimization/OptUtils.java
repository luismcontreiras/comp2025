package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.collections.AccumulatorMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import static pt.up.fe.comp2025.ast.Kind.TYPE;

/**
 * Utility methods related to the optimization middle-end.
 */
public class OptUtils {

    private final AccumulatorMap<String> temporaries;

    private final TypeUtils types;

    public OptUtils(TypeUtils types) {
        this.types = types;
        this.temporaries = new AccumulatorMap<>();
    }

    public String nextTemp() {
        return nextTemp("tmp");
    }

    public String nextTemp(String prefix) {
        // Subtract 1 because the base is 1
        var nextTempNum = temporaries.add(prefix) - 1;
        return prefix + nextTempNum;
    }

    public String toOllirType(JmmNode typeNode) {
        TYPE.checkOrThrow(typeNode);
        return toOllirType(types.convertType(typeNode));
    }

    public static String toOllirType(Type type) {
        // Add null check to prevent NullPointerException
        if (type == null) {
            // Default to void when type is null
            return ".V";
        }

        String result = "";
        if (type.isArray()) {
            result += ".array";
        }
        return result + toOllirType(type.getName());
    }

    private static String toOllirType(String typeName) {
        // Add null check for type name as well
        if (typeName == null) {
            return ".V"; // Default to void for null type name
        }

        String type = "." + switch (typeName) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            case "String" -> "String";
            case "void" -> "V";
            default -> typeName; // Use the type name directly instead of throwing exception
        };

        return type;
    }
}
