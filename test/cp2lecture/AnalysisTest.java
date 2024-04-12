package cp2lecture;

import org.junit.Test;
import pt.up.fe.comp.TestUtils;
import pt.up.fe.specs.util.SpecsIo;

public class AnalysisTest {
    @Test
    public void test() {
        var results = TestUtils.analyse(SpecsIo.getResource("cp2lecture/HelloWorld.jmm"));
        System.out.println("Symbol Table: " + results.getSymbolTable().print());
        TestUtils.noErrors(results);
    }
}
