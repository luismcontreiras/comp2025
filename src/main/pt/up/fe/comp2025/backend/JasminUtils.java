package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.type.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.HashMap;
import java.util.Map;

import org.specs.comp.ollir.type.Type;

public class JasminUtils {

    private final OllirResult ollirResult;

    public JasminUtils(OllirResult ollirResult) {
        // Can be useful to have if you expand this class with more methods
        this.ollirResult = ollirResult;
    }


    public String getModifier(AccessModifier accessModifier) {
        return switch (accessModifier) {
            case PUBLIC -> "public ";
            case PRIVATE -> "private ";
            case PROTECTED -> "protected ";
            default -> "";
        };
    }

    public String getJasminType(Object type) {
        String typeStr = type.toString().toLowerCase();

        if (typeStr.contains("int")) {
            return "I";
        }

        if (typeStr.contains("boolean")) {
            return "Z";
        }

        if (typeStr.contains("array")) {
            return "[I"; // Assuming int arrays
        }

        if (typeStr.contains("void")) {
            return "V";
        }

        if (typeStr.contains("this") || typeStr.contains("object") || typeStr.contains("class")) {
            // Generic reference/object type
            return "Ljava/lang/Object;";
        }

        // Fallback for custom class names
        return "L" + typeStr + ";";
    }



}
