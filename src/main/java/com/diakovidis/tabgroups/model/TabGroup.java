package com.diakovidis.tabgroups.model;

/**
 * Represents a tab group with a name, order priority, and a regex pattern
 * that matches against the full path of referenced files.
 */
public class TabGroup {

    private String name;
    private int order;
    private String regex;
    private boolean enabled;

    public TabGroup() {
        this.name = "";
        this.order = 0;
        this.regex = "";
        this.enabled = true;
    }

    public TabGroup(String name, int order, String regex) {
        this.name = name;
        this.order = order;
        this.regex = regex != null ? regex : "";
        this.enabled = true;
    }

    public TabGroup(String name, int order, String regex, boolean enabled) {
        this.name = name;
        this.order = order;
        this.regex = regex != null ? regex : "";
        this.enabled = enabled;
    }

    /**
     * Creates a deep copy of this TabGroup.
     */
    public TabGroup copy() {
        return new TabGroup(this.name, this.order, this.regex, this.enabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public String getRegex() {
        return regex;
    }

    public void setRegex(String regex) {
        this.regex = regex != null ? regex : "";
    }

    @Override
    public String toString() {
        return name + " (order=" + order + ", regex=" + regex + ", enabled=" + enabled + ")";
    }
}
