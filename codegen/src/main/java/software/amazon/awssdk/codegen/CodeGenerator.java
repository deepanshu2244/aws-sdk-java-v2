/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.codegen;

import com.squareup.javapoet.ClassName;
import java.io.File;
import java.util.concurrent.ForkJoinTask;
import software.amazon.awssdk.codegen.emitters.GeneratorTask;
import software.amazon.awssdk.codegen.emitters.GeneratorTaskParams;
import software.amazon.awssdk.codegen.emitters.tasks.AwsGeneratorTasks;
import software.amazon.awssdk.codegen.internal.Utils;
import software.amazon.awssdk.codegen.model.intermediate.IntermediateModel;
import software.amazon.awssdk.codegen.model.intermediate.Protocol;

public class CodeGenerator {

    private static final String MODEL_DIR_NAME = "models";

    private final C2jModels models;
    private final String sourcesDirectory;
    private final String testsDirectory;

    static {
        // Make sure ClassName is statically initialized before we do anything in parallel.
        // Parallel static initialization of ClassName and TypeName can result in a deadlock:
        // https://github.com/square/javapoet/issues/799
        ClassName.get(Object.class);
    }

    public CodeGenerator(Builder builder) {
        this.models = builder.models;
        this.sourcesDirectory = builder.sourcesDirectory;
        this.testsDirectory = builder.testsDirectory;
    }

    public static File getModelDirectory(String outputDirectory) {
        File dir = new File(outputDirectory, MODEL_DIR_NAME);
        Utils.createDirectory(dir);
        return dir;
    }

    /**
     * @return Builder instance to construct a {@link CodeGenerator}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * load ServiceModel. load code gen configuration from individual client. generate intermediate model. generate
     * code.
     */
    public void execute() {
        try {
            IntermediateModel intermediateModel = new IntermediateModelBuilder(models).build();

            emitCode(intermediateModel);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to generate code. Exception message : " + e.getMessage(), e);

        }
    }

    private void emitCode(IntermediateModel intermediateModel) {
        ForkJoinTask.invokeAll(createGeneratorTasks(intermediateModel));
    }

    private GeneratorTask createGeneratorTasks(IntermediateModel intermediateModel) {
        // For clients built internally, the output directory and source directory are the same.
        GeneratorTaskParams params = GeneratorTaskParams.create(intermediateModel, sourcesDirectory, testsDirectory);

        if (params.getModel().getMetadata().getProtocol() == Protocol.API_GATEWAY) {
            throw new UnsupportedOperationException("Unsupported protocol: " + Protocol.API_GATEWAY);
        } else {
            return new AwsGeneratorTasks(params);
        }
    }

    /**
     * Builder for a {@link CodeGenerator}.
     */
    public static final class Builder {

        private C2jModels models;
        private String sourcesDirectory;
        private String testsDirectory;

        private Builder() {
        }

        public Builder models(C2jModels models) {
            this.models = models;
            return this;
        }

        public Builder sourcesDirectory(String sourcesDirectory) {
            this.sourcesDirectory = sourcesDirectory;
            return this;
        }

        public Builder testsDirectory(String smokeTestsDirectory) {
            this.testsDirectory = smokeTestsDirectory;
            return this;
        }

        /**
         * @return An immutable {@link CodeGenerator} object.
         */
        public CodeGenerator build() {
            return new CodeGenerator(this);
        }
    }

}
