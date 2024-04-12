package cp2lecture;

import org.junit.Test;
import pt.up.fe.comp.TestUtils;
import pt.up.fe.specs.util.SpecsIo;

public class OptimizationTest {
    @Test
    public void test() {
        var ollirResult = TestUtils.optimize(SpecsIo.getResource("cp2lecture/HelloWorld.jmm"));
        TestUtils.noErrors(ollirResult);
    }
}
