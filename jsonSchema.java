import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaId;
import com.networknt.schema.InputFormat;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;

public class SchemaValidator {
    public static void main(String[] args) throws Exception {
        // 1. Read the candidate schema file (as JSON text)
        String schemaContent = Files.readString(Paths.get("your-schema.json"));

        // 2. Create a factory for Draft 2020-12 (fallback dialect if $schema is absent)
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(VersionFlag.V202012);
        // :contentReference[oaicite:7]{index=7}

        // 3. Build default validator configuration
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().build();
        // (enable format assertions if desired via builder.setFormatAssertionsEnabled(true)) :contentReference[oaicite:8]{index=8}

        // 4. Load the Draft 2020-12 Meta-Schema from classpath
        JsonSchema metaSchema = factory.getSchema(
            SchemaLocation.of(SchemaId.V202012),
            config
        );
        // :contentReference[oaicite:9]{index=9}

        // 5. Validate the schema content against the Meta-Schema
        Set<ValidationMessage> errors = metaSchema.validate(
            schemaContent,
            InputFormat.JSON,
            // optional: executionContext -> executionContext.getExecutionConfig().setFormatAssertionsEnabled(true)
        );
        // :contentReference[oaicite:10]{index=10}

        // 6. Report results
        if (errors.isEmpty()) {
            System.out.println("✓ Schema is valid against Draft 2020-12 Meta-Schema");
        } else {
            System.err.println("✗ Schema validation errors:");
            errors.forEach(vm ->
                System.err.printf("  [%s] %s (at %s)%n",
                    vm.getType(), vm.getMessage(), vm.getPath())
            );
        }
    }
}
