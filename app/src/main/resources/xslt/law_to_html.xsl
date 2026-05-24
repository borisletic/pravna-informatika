<?xml version="1.0" encoding="UTF-8"?>
<!--
    XSLT: Akoma Ntoso zakon -> HTML
    VLASNIK: Član 3 (Application), uz konsultaciju Člana 1
    CELINA: 7

    XSLT 1.0 kompatibilan (radi sa Saxon-HE, lxml, xsltproc).
    XML koristi default Akoma Ntoso namespace, pa u XPath-u
    koristimo akn: prefix mapiran na isti namespace.

    Šta hvata:
      - chapter -> <section class="chapter">
      - article -> <article class="article" id="art_260">
      - paragraph -> <div class="paragraph" id="art_260__para_1">
      - ref -> <a class="ref" href="#art_260">
-->
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:akn="http://docs.oasis-open.org/legaldocml/ns/akn/3.0"
                exclude-result-prefixes="akn">

    <xsl:output method="html" indent="yes" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <div class="law">
            <xsl:apply-templates select="//akn:body"/>
        </div>
    </xsl:template>

    <xsl:template match="akn:chapter">
        <section class="chapter" id="{@eId}">
            <h2 class="chapter-heading">
                <span class="chapter-num"><xsl:value-of select="akn:num"/></span>
                <xsl:text> &#8211; </xsl:text>
                <span class="chapter-title"><xsl:value-of select="akn:heading"/></span>
            </h2>
            <xsl:apply-templates select="akn:article"/>
        </section>
    </xsl:template>

    <xsl:template match="akn:article">
        <article class="article" id="{@eId}">
            <h3 class="article-heading">
                <span class="article-num"><xsl:value-of select="akn:num"/></span>
                <xsl:if test="akn:heading">
                    <xsl:text> &#8211; </xsl:text>
                    <span class="article-title"><xsl:value-of select="akn:heading"/></span>
                </xsl:if>
            </h3>
            <xsl:apply-templates select="akn:paragraph"/>
        </article>
    </xsl:template>

    <xsl:template match="akn:paragraph">
        <div class="paragraph" id="{@eId}">
            <xsl:if test="akn:num and string-length(normalize-space(akn:num)) &gt; 0">
                <span class="para-num"><xsl:value-of select="akn:num"/></span>
                <xsl:text> </xsl:text>
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
        <a class="ref" href="{@href}">
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
