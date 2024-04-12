package pt.up.fe.comp.ollir;

import pt.up.fe.comp.analysis.SymbolTableBuilder;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.Collections;

public class JmmOptimizer implements JmmOptimization {
    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {

        var ollirGenerator = new OllirGenerator((SymbolTableBuilder) semanticsResult.getSymbolTable());

        var ollirCode =  ollirGenerator.visit(semanticsResult.getRootNode()).code;

        /*String ollirCode = "import ioPlus;\n" +
                "import BoardBase;\n" +
                "import java.io.File;\n" +
                "\n" +
                "public HelloWorld extends BoardBase{\n" +
                "\t.method public static main(args.array.String).V {\n" +
                "\t\tinvokestatic(ioPlus, \"printHelloWorld\").V;\n" +
                "\t}\n" +

                "}";

*/

        System.out.println("Semantics Result: \n" + semanticsResult.getSymbolTable().print());
        System.out.println("OLlir code: \n" + ollirCode);


        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());

    }
}
