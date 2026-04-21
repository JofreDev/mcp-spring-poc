package com.example.mcpdemo.manifest.model;

import java.util.List;

public record PromptDescriptor(String name,
                               String title,
                               String description,
                               List<PromptArgumentDescriptor> arguments,
                               String template,
                               boolean excluded) {

    // Cambiar argumento xd
    public PromptDescriptor withOverride(PromptDescriptor override) {
        if (override == null) {
            return this;
        }

        return new PromptDescriptor(
                override.name != null ? override.name() : this.name,
                override.title != null ? override.title() : this.title,
                override.description != null ? override.description : this.description,
                override.arguments != null
                        ? arguments.stream().flatMap(arg ->
                        override.arguments.stream()
                                .map(argOver -> arg.name().equals(argOver.name())
                                        ? arg.merge(argOver)
                                        : arg)).toList()
                        : this.arguments,
                override.template != null ? override.template : this.template,
                override.excluded != excluded ? override.excluded : this.excluded

        );

    }
}
