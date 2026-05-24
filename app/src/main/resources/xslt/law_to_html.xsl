<?xml version="1.0" encoding="UTF-8"?>
<!--
    XSLT: Akoma Ntoso zakon -> HTML
    VLASNIK: Član 3 (Application), uz konsultaciju Člana 1 (zna AN strukturu)
    CELINA: 7

    TODO: razraditi templates za sve elemente koji se anotiraju u Celini 1.
    Trenutno - minimalan skelet.
-->
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:akn="http://docs.oasis-open.org/legaldocml/ns/akn/3.0"
                exclude-result-prefixes="akn">

    <xsl:output method="html" indent="yes"/>

    <xsl:template match="/">
        <div class="law">
            <xsl:apply-templates select="//akn:body"/>
        </div>
    </xsl:template>

    <xsl:template match="akn:chapter">
        <section class="chapter">
            <h2>
                <xsl:value-of select="akn:num"/> -
                <xsl:value-of select="akn:heading"/>
            </h2>
            <xsl:apply-templates select="akn:article"/>
        </section>
    </xsl:template>

    <xsl:template match="akn:article">
        <article class="article" id="{@eId}">
            <h3>
                <span class="article-num"><xsl:value-of select="akn:num"/></span>
                <xsl:text> </xsl:text>
                <span class="article-heading"><xsl:value-of select="akn:heading"/></span>
            </h3>
            <xsl:apply-templates select="akn:paragraph"/>
        </article>
    </xsl:template>

    <xsl:template match="akn:paragraph">
        <div class="paragraph" id="{@eId}">
            <xsl:if test="akn:num">
                <span class="para-num"><xsl:value-of select="akn:num"/></span>
                <xsl:text> </xsl:text>
            </xsl:if>
            <xsl:apply-templates select="akn:content"/>
        </div>
    </xsl:template>

    <xsl:template match="akn:ref">
        <a class="ref" href="{@href}">
            <xsl:value-of select="."/>
        </a>
    </xsl:template>

    <!-- Pass-through za nepoznate elemente: prikaži tekst -->
    <xsl:template match="*">
        <xsl:apply-templates/>
    </xsl:template>

</xsl:stylesheet>
