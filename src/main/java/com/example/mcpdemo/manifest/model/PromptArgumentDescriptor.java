package com.example.mcpdemo.manifest.model;

public record PromptArgumentDescriptor(String name,
                                       String description,
                                       boolean required
) {

    public PromptArgumentDescriptor merge(PromptArgumentDescriptor override){
        if (override == null) return this;

        return new PromptArgumentDescriptor(
                override.name != null ? override.name() : this.name,
                override.description != null ? override.description : this.description,
                override.name != null ? override.required : this.required

        );

    }
}
