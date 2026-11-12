package dev.liveftl.preview.render;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.Version;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VariableNameTemplateModelTest {
    @Test
    void rendersMissingAndNestedVariablesAsTheirNames() throws Exception {
        Configuration configuration = new Configuration(new Version("2.3.34"));
        Template template = new Template(
                "placeholder-test",
                "${title}|${customer.name}|${items[0]}|${optional!\"\"}",
                configuration
        );
        StringWriter output = new StringWriter();

        template.process(VariableNameTemplateModel.root(), output);

        assertEquals("title|customer.name|items[0]|optional", output.toString());
    }
}
