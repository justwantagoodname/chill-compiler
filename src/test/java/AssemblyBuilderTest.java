import top.voidc.misc.AssemblyBuilder;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

class AssemblyBuilderTest {
    private AssemblyBuilder assemblyBuilder;
    private final String TEST_FILE = "test.txt";

    @BeforeEach
    void setUp() throws IOException {
        assemblyBuilder = new AssemblyBuilder(TEST_FILE);
    }

    @AfterEach
    void tearDown() throws IOException {
        File file = new File(TEST_FILE);
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    void testWriteRaw() throws IOException {
        assemblyBuilder.writeRaw("Hello, World!");
        assemblyBuilder.close();

        BufferedReader reader = new BufferedReader(new FileReader(TEST_FILE));
        assertEquals("Hello, World!", reader.readLine());
        reader.close();
    }

    @Test
    void testCProgram() throws IOException {
        assemblyBuilder.writeRaw("#include <stdio.h>\n");
        assemblyBuilder.writeRaw("int main() {\n");
        assemblyBuilder.writeRaw("   printf(\"Hello, World!\\n\");\n");
        assemblyBuilder.writeRaw("   return 0;\n");
        assemblyBuilder.writeRaw("}\n");
        assemblyBuilder.close();

        BufferedReader reader = new BufferedReader(new FileReader(TEST_FILE));
        assertEquals("#include <stdio.h>", reader.readLine());
        assertEquals("int main() {", reader.readLine());
        assertEquals("   printf(\"Hello, World!\\n\");", reader.readLine());
        assertEquals("   return 0;", reader.readLine());
        assertEquals("}", reader.readLine());
        reader.close();
    }

    @Test
    void testFormat() throws IOException {
        assemblyBuilder.writeLine("1 + 1 = %d", 2);
        assemblyBuilder.close();

        BufferedReader reader = new BufferedReader(new FileReader(TEST_FILE));
        assertEquals("1 + 1 = 2", reader.readLine());
        reader.close();
    }
}
