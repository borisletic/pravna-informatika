<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:akn="http://docs.oasis-open.org/legaldocml/ns/akn/3.0"
                exclude-result-prefixes="akn">

    <xsl:output method="html" indent="yes"/>

    <xsl:template match="/">
        <div class="judgment-content">
            <header class="judgment-header">
                <div class="judgment-meta">
                    <span class="case-num">
                        <xsl:value-of select="//akn:FRBRalias[@name='caseNumber']/@value"/>
                    </span>
                    <span class="court">
                        <xsl:value-of select="//akn:FRBRalias[@name='court']/@value"/>
                    </span>
                    <span class="judgment-date">
                        <xsl:value-of select="//akn:FRBRdate[@name='judgment']/@date"/>
                    </span>
                </div>
            </header>
            <xsl:apply-templates select="//akn:judgmentBody"/>
        </div>
    </xsl:template>

    <xsl:template match="akn:background">
        <section class="judgment-section">
            <h3 class="section-title">Činjenično stanje</h3>
            <div class="section-body">
                <xsl:apply-templates/>
            </div>
        </section>
    </xsl:template>

    <xsl:template match="akn:motivation">
        <section class="judgment-section">
            <h3 class="section-title">Obrazloženje</h3>
            <div class="section-body">
                <xsl:apply-templates/>
            </div>
        </section>
    </xsl:template>

    <xsl:template match="akn:decision">
        <section class="judgment-section decision-section">
            <h3 class="section-title">Dispozitiv</h3>
            <div class="section-body">
                <xsl:apply-templates/>
            </div>
        </section>
    </xsl:template>

    <xsl:template match="akn:introduction">
        <section class="judgment-section">
            <h3 class="section-title">Uvod</h3>
            <div class="section-body">
                <xsl:apply-templates/>
            </div>
        </section>
    </xsl:template>

    <xsl:template match="akn:p">
        <p><xsl:apply-templates/></p>
    </xsl:template>

    <xsl:template match="akn:ref">
        <a class="ref judgment-ref" href="{@href}">
            <xsl:value-of select="."/>
        </a>
    </xsl:template>

    <xsl:template match="*">
        <xsl:apply-templates/>
    </xsl:template>

</xsl:stylesheet>
