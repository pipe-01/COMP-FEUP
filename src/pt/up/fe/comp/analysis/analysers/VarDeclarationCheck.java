package pt.up.fe.comp.analysis.analysers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pt.up.fe.comp.analysis.SemanticAnalyser;
import pt.up.fe.comp.analysis.SymbolTableBuilder;
import pt.up.fe.comp.ast.AstNode;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;

public class VarDeclarationCheck extends PreorderJmmVisitor<SymbolTableBuilder, Integer> implements SemanticAnalyser {

    private List<Report> reports;
    private String current_Method;


    public VarDeclarationCheck(){
        reports = new ArrayList<>();
        current_Method = null;

        addVisit(AstNode.METHOD_DECLARATION.toString(), this::methodDeclarationVisit);
        addVisit(AstNode.IDENTIFIER.toString(), this::identifierVisit);
    }

    private Integer methodDeclarationVisit(JmmNode methodDeclarationNode, SymbolTable symbolTable) {
        var methodName = methodDeclarationNode.getJmmChild(1).get("name");
        current_Method = methodName;

        return 0;

    }

    private Integer identifierVisit(JmmNode identifierNode, SymbolTableBuilder symbolTable) {
        //If identifier is in a method and not a local declaration
        if(identifierNode.getJmmParent().getKind().equals("LocalVarDeclaration") || current_Method==null){ return 0; }
        if(identifierNode.getJmmParent().getKind().equals("MemberCall")){return 0;}

        var identifierName = identifierNode.get("name");

        //Verify if it is a constructor
        if(identifierNode.getJmmParent().getKind().equals("Constructor")){
            for( String imp : symbolTable.getImports()){
                if(identifierName.equals(importName(imp))){
                    return 0;
                }
            }

            if(symbolTable.getClassName().equals(identifierName)) return 0;
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1,-1, "Identifier '" + identifierName + "' is not valid class"));
            return -1;
        }

        //Verify if current method was valid
        //If error was on a previous analyser, can not be on the symbol table
        if(!symbolTable.getMethods().contains(current_Method)) return 0;

        //Verify if it is on the local variables
        if(symbolTable.hasLocalVariable(current_Method,identifierName)){
            return 0;
        }
        //Verify if it is on the param
        for( Symbol param : symbolTable.getParameters(current_Method)){
            if(param.getName().equals(identifierName)) return 0;
        }

        //Verify if it is on the global variables
        if(symbolTable.hasField(identifierName))return 0;

        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(identifierNode.get("line")),Integer.valueOf(identifierNode.get("col")), "Identifier '" + identifierName + "' is not declared"));

        return -1;
    }

    private String importName(String imp){
        String[] impNames = imp.split("\\.");
        return impNames[impNames.length-1];
    }


    @Override
    public List<Report> getReports() {
        return reports;
    }
}
