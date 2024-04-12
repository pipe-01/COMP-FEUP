package cp2lecture;

import org.junit.Test;
import pt.up.fe.comp.TestUtils;
import pt.up.fe.specs.util.SpecsIo;

public class JasminTest {
    @Test
    public void test() {
        var jasminResult = TestUtils.backend(SpecsIo.getResource("cp2lecture/HelloWorld.jmm"));
        TestUtils.noErrors(jasminResult);

        jasminResult.compile();
        //String result = jasminResult.run();
        //System.out.println("Jasmin: " + result);
    }
}
