package dev.liveftl.preview.render;

import freemarker.template.TemplateBooleanModel;
import freemarker.template.TemplateHashModel;
import freemarker.template.TemplateScalarModel;
import freemarker.template.TemplateSequenceModel;

final class VariableNameTemplateModel
        implements TemplateHashModel, TemplateScalarModel, TemplateBooleanModel, TemplateSequenceModel {
    private final String name;

    private VariableNameTemplateModel(String name) {
        this.name = name;
    }

    static VariableNameTemplateModel root() {
        return new VariableNameTemplateModel("");
    }

    @Override
    public VariableNameTemplateModel get(String key) {
        return new VariableNameTemplateModel(name.isEmpty() ? key : name + "." + key);
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public String getAsString() {
        return name;
    }

    @Override
    public boolean getAsBoolean() {
        return false;
    }

    @Override
    public VariableNameTemplateModel get(int index) {
        if (index != 0) {
            return null;
        }
        return new VariableNameTemplateModel(name + "[" + index + "]");
    }

    @Override
    public int size() {
        return 1;
    }
}
