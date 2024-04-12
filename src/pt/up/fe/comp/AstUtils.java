package pt.up.fe.comp;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.specs.util.SpecsCheck;

public class AstUtils {
    public static Type buildReturnType(JmmNode type){

        SpecsCheck.checkArgument(type.getKind().equals("ReturnType"), ()->"Expected node of type 'Type', got " + type.getKind() + " ");

        var typeName = type.get("name");
        var isArray = type.getOptional("isArray").map(isArrayString -> Boolean.valueOf(isArrayString)).orElse(false);

        return new Type(typeName, isArray);
    }

    public static Type buildTypeParam(JmmNode type){

        SpecsCheck.checkArgument(type.getKind().equals("TypeParam"), ()->"Expected node of type 'Type', got " + type.getKind() + " ");

        var typeName = type.get("name");
        var isArray = type.getOptional("isArray").map(isArrayString -> Boolean.valueOf(isArrayString)).orElse(false);

        return new Type(typeName, isArray);
    }

    public static Type buildType(JmmNode type) {
        SpecsCheck.checkArgument(type.getKind().equals("Type"), ()->"Expected node of type 'Type', got " + type.getKind() + " ");

        var typeName = type.get("name");
        var isArray = type.getOptional("isArray").map(isArrayString -> Boolean.valueOf(isArrayString)).orElse(false);

        return new Type(typeName, isArray);

    }
}
