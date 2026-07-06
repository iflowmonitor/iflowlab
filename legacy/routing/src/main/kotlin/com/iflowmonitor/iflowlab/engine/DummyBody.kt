package com.iflowmonitor.iflowlab.engine

/**
 * The canonical CPI header-only dummy body, injected byte-for-byte when a case declares neither
 * `body:` nor `bodyFile:` (PRD §D5). Expanded form, no namespace, no XML prolog. P7 hardens this
 * with a drift-guard test and the "header-only (dummy body)" runner label.
 */
const val DUMMY_BODY: String = "<dummy></dummy>"
