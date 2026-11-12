# Live FTL Preview

An IntelliJ IDEA plugin that renders the selected `.ftl` file in a tool window on the right. The preview refreshes automatically as you edit the template.

Use the **HTML** / **PDF** toggle to choose the output:

- **HTML** shows the FreeMarker-rendered output in Chromium, matching the HTML preview flow used by `communication-hub`.
- **PDF** runs the rendered output through Flying Saucer, as `pdf-generator` does, then displays each generated PDF page in the browser. Projects rooted in a directory named `pdf-generator` default to PDF; other projects default to HTML.

The HTML preview uses a white canvas, light browser controls, and the standard 8-pixel browser body margin unless the template supplies its own styles. PDF mode reflects the actual PDF page layout and pagination.

Use the save icon in the preview toolbar to export the current rendered template as HTML, PDF, or PNG. PNG export produces one file per PDF page; multipage files are named `template-1.png`, `template-2.png`, and so on beside the selected destination.

## Variable placeholders

The preview renders variable references as their names instead of looking up runtime values. For example, `${customer.name}` appears as `customer.name`, so templates can be previewed without application data.

Templates with `<#include>` or `<#import>` can resolve files elsewhere within the IntelliJ project directory, including shared templates in sibling or parent directories. The edited template is rendered from the editor buffer; imported templates are read from disk. File access is confined to the project directory (or the containing content root for files outside it). Template syntax and data errors appear in the preview status.

## Build and run

Open this directory in IntelliJ IDEA, or run:

```shell
./gradlew runIde
```

The sandbox IDE opens with the plugin installed. Select an `.ftl` file to show the preview tool window.

To test the installed plugin, open `example.ftl` from this project in IntelliJ IDEA. Its preview should show the variable names (`page.title`, `customer.name`, and `items[0]`) as text, and update as you edit the file.
