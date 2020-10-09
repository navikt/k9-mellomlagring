package no.nav.helse.dokument.eier

data class Eier(val id : String)
enum class HentEierFra { ACCESS_TOKEN_SUB_CLAIM, QUERY_PARAMETER_EIER }