package pt.up.fe.comp;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import pt.up.fe.comp.analysis.JmmAnalyser;
import pt.up.fe.comp.jasmin.JasminEmitter;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.ollir.JmmOptimizer;
import pt.up.fe.specs.util.SpecsIo;
import pt.up.fe.specs.util.SpecsLogs;
import pt.up.fe.specs.util.SpecsSystem;

public class Launcher {

    public static void main(String[] args) {
        SpecsSystem.programStandardInit();

        SpecsLogs.info("Executing with args: " + Arrays.toString(args));

        // read the input code
        if (args.length != 1) {
            throw new RuntimeException("Expected a single argument, a path to an existing input file.");
        }
        File inputFile = new File(args[0]);
        if (!inputFile.isFile()) {
            throw new RuntimeException("Expected a path to an existing input file, got '" + args[0] + "'.");
        }
        String input = SpecsIo.read(inputFile);

        // Create config
        Map<String, String> config = new HashMap<>();
        config.put("inputFile", args[0]);
        config.put("optimize", "false");
        config.put("registerAllocation", "-1");
        config.put("debug", "false");

        // Instantiate JmmParser
        SimpleParser parser = new SimpleParser();

        // Parse stage
        JmmParserResult parserResult = parser.parse(input, config);

        // Check if there are parsing errors
        TestUtils.noErrors(parserResult.getReports());

        // Instantiate JmmAlysis
        var analyser = new JmmAnalyser();

        // Analysis stage
        var semanticsResult = analyser.semanticAnalysis(parserResult);

        // Check if there are parsing errors
        TestUtils.noErrors(semanticsResult);


        // Instantiate JmmOptimization
        var optimizer = new JmmOptimizer();

        // Optimization stage
        var optimizerResult1 = optimizer.optimize(semanticsResult);

        var optimizerResult2 = optimizer.toOllir(optimizerResult1);

        var ollirResult = optimizer.optimize(optimizerResult2);

        // Check if there are parsing errors
        TestUtils.noErrors(ollirResult);

        // Instantiate JasminBackend
        var jasminEmitter = new JasminEmitter();

        // Jasmin stage
        var jasminResult = jasminEmitter.toJasmin(ollirResult);

        // Check if there are parsing errors
        TestUtils.noErrors(jasminResult);

        var compiled = jasminResult.compile();
        System.out.printf(compiled.toString());

        SpecsIo.copy(compiled, new File("./libs-jmm/compiled/" + compiled.getName()));
        // ... add remaining stages
    }

}
