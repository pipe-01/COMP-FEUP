package cp2lecture;

import org.junit.Test;
import pt.up.fe.comp.TestUtils;
import pt.up.fe.specs.util.SpecsIo;

public class Cp2LectureTest {
    @Test
    public void testParse() {
        var results = TestUtils.parse(SpecsIo.getResource("cp2lecture/HelloWorld.jmm"));
        TestUtils.noErrors(results);
        System.out.println(results.getRootNode().toTree());
    }
}
