<?xml version="1.0" encoding="UTF-8"?>
<!--
    XSLT: Akoma Ntoso sudska odluka -> HTML
    VLASNIK: Član 3, uz konsultaciju Člana 2 (zna strukturu anotacije odluka)
    CELINA: 7

    TODO: razraditi za sve elemente koji se anotiraju u Celini 2.
-->
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:akn="http://docs.oasis-open.org/legaldocml/ns/akn/3.0"
                exclude-result-prefixes="akn">

    <xsl:output method="html" indent="yes"/>

    <xsl:template match="/">
        <div class="judgment">
            <header class="judgment-header">
                <h2>
                    <span class="case-num">
                        <xsl:value-of select="//akn:identification//akn:FRBRalias[@name='caseNumber']/@value"/>
                    </span>
                </h2>
                <p class="court">
                    <xsl:value-of select="//akn:identification//akn:FRBRauthor/@href"/>
                </p>
            </header>
            <xsl:apply-templates select="//akn:judgmentBody"/>
        </div>
    </xsl:template>

    <xsl:template match="akn:background">
        <section class="background">
            <h3>Činjenično stanje</h3>
            <xsl:apply-templates/>
        </section>
    </xsl:template>

    <xsl:template match="akn:motivation">
        <section class="motivation">
            <h3>Obrazloženje</h3>
            <xsl:apply-templates/>
        </section>
    </xsl:template>

    <xsl:template match="akn:decision">
        <section class="decision">
            <h3>Dispozitiv</h3>
            <xsl:apply-templates/>
        </section>
    </xsl:template>

    <xsl:template match="akn:ref">
        <a class="ref" href="{@href}">
            <xsl:value-of select="."/>
        </a>
    </xsl:template>

    <xsl:template match="*">
        <xsl:apply-templates/>
    </xsl:template>

</xsl:stylesheet>
