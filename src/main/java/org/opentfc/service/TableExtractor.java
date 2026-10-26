package org.opentfc.service;

import org.opentfc.model.TableModel;

/**
 * Service for extracting structured TableModel from raw HTML/XHTML or macro bodies.
 */
public interface TableExtractor {

    /**
     * Parse HTML/XHTML table body into a normalized TableModel.
     *
     * @param htmlContent Raw HTML/XHTML markup
     * @param tableId Unique identifier for the table
     * @return TableModel containing parsed columns and rows
     */
    TableModel extractTable(String htmlContent, String tableId);
}
