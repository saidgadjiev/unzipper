package ru.gadjini.any2any.service.conversion.api;

public enum Format {

    PPTX(FormatCategory.DOCUMENTS),
    PPT(FormatCategory.DOCUMENTS),
    PPTM(FormatCategory.DOCUMENTS),
    POTX(FormatCategory.DOCUMENTS),
    POT(FormatCategory.DOCUMENTS),
    POTM(FormatCategory.DOCUMENTS),
    PPS(FormatCategory.DOCUMENTS),
    PPSX(FormatCategory.DOCUMENTS),
    PPSM(FormatCategory.DOCUMENTS),
    XLSX(FormatCategory.DOCUMENTS),
    XLS(FormatCategory.DOCUMENTS),
    DOC(FormatCategory.DOCUMENTS),
    DOCX(FormatCategory.DOCUMENTS),
    RTF(FormatCategory.DOCUMENTS),
    PDF(FormatCategory.DOCUMENTS),
    PNG(FormatCategory.IMAGES),
    HEIC(FormatCategory.IMAGES),
    HEIF(FormatCategory.IMAGES),
    ICO(FormatCategory.IMAGES),
    SVG(FormatCategory.IMAGES),
    JPG(FormatCategory.IMAGES),
    JP2(FormatCategory.IMAGES),
    BMP(FormatCategory.IMAGES),
    TXT(FormatCategory.DOCUMENTS),
    TIFF(FormatCategory.IMAGES),
    EPUB(FormatCategory.DOCUMENTS),
    WEBP(FormatCategory.IMAGES),
    PHOTO(FormatCategory.IMAGES),
    TGS(FormatCategory.IMAGES),
    GIF(FormatCategory.IMAGES),
    STICKER(FormatCategory.IMAGES) {
        @Override
        public String getExt() {
            return "webp";
        }
    },
    HTML(FormatCategory.WEB),
    URL(FormatCategory.WEB),
    TEXT(FormatCategory.DOCUMENTS),
    ZIP(FormatCategory.ARCHIVE),
    RAR(FormatCategory.ARCHIVE);

    private FormatCategory category;

    Format(FormatCategory category) {
        this.category = category;
    }

    public String getExt() {
        return name().toLowerCase();
    }

    public FormatCategory getCategory() {
        return category;
    }
}
