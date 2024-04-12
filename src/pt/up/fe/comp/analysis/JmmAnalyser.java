package pt.up.fe.comp.analysis;

import org.eclipse.jgit.util.SystemReader;

import pt.up.fe.comp.analysis.analysers.SingleMainMethodCheck;
import pt.up.fe.comp.analysis.analysers.TypeCheckingVisitor;
import pt.up.fe.comp.analysis.analysers.VarDeclarationCheck;
import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class JmmAnalyser implements JmmAnalysis {
    @Override
    public JmmSemanticsResult semanticAnalysis(JmmParserResult parserResult) {

        List<Report> reports = new ArrayList<>();

        var symbolTable = new SymbolTableBuilder();

        var symbolTableFiller = new SymbolTableFiller();

        symbolTableFiller.visit(parserResult.getRootNode(), symbolTable);

        reports.addAll(symbolTableFiller.getReports());

        if(!reports.isEmpty()){
            return new JmmSemanticsResult(parserResult, symbolTable, reports);
        }

        var analyser = new VarDeclarationCheck();

        analyser.visit(parserResult.getRootNode(),symbolTable);

        reports.addAll(analyser.getReports());
        if(!reports.isEmpty()){
            return new JmmSemanticsResult(parserResult, symbolTable, reports);
        }


        var analyser1 = new TypeCheckingVisitor();

        analyser1.visit(parserResult.getRootNode(),symbolTable);

        reports.addAll(analyser1.getReports());


        return new JmmSemanticsResult(parserResult, symbolTable, reports);
    }
}
