package de.leidenheit.infrastructure.parsing;

public interface ParserExtension {

    ParseResult readLocation(final String arazzoUrl, final ParseOptions options);
    ParseResult readContents(final String arazzoAsString, final ParseOptions options);
}
