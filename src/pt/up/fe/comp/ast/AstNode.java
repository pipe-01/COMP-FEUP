package pt.up.fe.comp.ast;

import pt.up.fe.specs.util.SpecsStrings;

public enum AstNode {
    START,
    IMPORT_DECLARATION,
    CLASS_DECLARATION,
    VAR_DECLARATION,
    METHOD_DECLARATION,
    METHOD_INTERIOR,
    LOCAL_VAR_DECLARATION,
    PARAM,
    CONSTRUCTOR,
    TYPE_PARAM,
    TYPE,
    RETURN_TYPE,
    RETURN_VALUE,
    ASSIGNMENT,
    IF_ELSE_STATEMENT,
    IF_STATEMENTS,
    ELSE_STATEMENTS,
    ARRAY_INDEX,
    ARRAY_ASSIGN,
    BIN_OP,
    UNARY_OP,
    IDENTIFIER,
    INTEGER_LITERAL,
    MEMBER_CALL,
    ARGUMENTS,
    BOOL_TRUE,
    WHILE_LOOP,
    WHILE_STATEMENTS,
    BOOL_FALSE,
    SELF_THIS,
    LEN,
    ID;

    private final String name;

    private AstNode(){
        this.name = SpecsStrings.toCamelCase(name(), "_", true);
    }

    @Override
    public String toString() {
        return name;
    }
}
