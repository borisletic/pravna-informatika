<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:akn="http://docs.oasis-open.org/legaldocml/ns/akn/3.0"
                exclude-result-prefixes="akn">

    <xsl:output method="html" indent="yes" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <div class="law-content">
            <xsl:apply-templates select="//akn:body"/>
        </div>
    </xsl:template>

    <xsl:template match="akn:chapter">
        <section class="chapter" id="{@eId}">
            <div class="chapter-header">
                <span class="chapter-label">
                    <xsl:value-of select="akn:num"/>
                </span>
                <h2 class="chapter-title">
                    <xsl:value-of select="akn:heading"/>
                </h2>
            </div>
            <xsl:apply-templates select="akn:article"/>
        </section>
    </xsl:template>

    <xsl:template match="akn:article">
        <article class="article" id="{@eId}">
            <div class="article-header">
                <span class="article-num"><xsl:value-of select="akn:num"/></span>
                <xsl:if test="akn:heading">
                    <span class="article-title"><xsl:value-of select="akn:heading"/></span>
                </xsl:if>
            </div>
            <div class="article-body">
                <xsl:apply-templates select="akn:paragraph"/>
            </div>
        </article>
    </xsl:template>

    <xsl:template match="akn:paragraph">
        <div class="paragraph" id="{@eId}">
            <xsl:if test="akn:num and string-length(normalize-space(akn:num)) &gt; 0">
                <span class="para-num"><xsl:value-of select="akn:num"/></span>
            </xsl:if>
            <xsl:apply-templates select="akn:content"/>
        </div>
    </xsl:template>

    <xsl:template match="akn:content">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="akn:p">
        <p><xsl:apply-templates/></p>
    </xsl:template>

    <xsl:template match="akn:ref">
        <a class="ref law-ref" href="{@href}">
            <xsl:value-of select="."/>
        </a>
    </xsl:template>

    <xsl:template match="*">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="text()">
        <xsl:value-of select="."/>
    </xsl:template>

</xsl:stylesheet>
