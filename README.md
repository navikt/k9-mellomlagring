# k9-mellomlagring

![](https://github.com/navikt/k9-mellomlagring/workflows/CI%20/%20CD/badge.svg)
![NAIS Alerts](https://github.com/navikt/k9-mellomlagring/workflows/Alerts/badge.svg)

Mellomlagrer vedlegg før innsending av søknad, og dokumenter før journalføring.

## API
### Tilgang
- Om Login Service er konfigurert som issuer må ID-token utstedt fra Login Service på Level4 sendes som "Authorization" header (Bearer schema). Da hentes eier av dokumentet fra tokenets `sub` claim. Det er da kun denne personen som kan operere på sine dokumenter.
- Om Nais STS og/eller Azure er konfigurert som issuere må Access Token sendes i "Authorization" header (Bearer schema). Da hentes eier av dokumentet fra query parameter `eier`. En Systembruker kan operere på alles dokumenter.

### Login Service
ID-Tokenet må være på Level4

### Nais STS
Access Tokenet må tilhøre en av `NAIS_STS_AUTHORIZED_CLIENTS`

### Azure
Access Tokenet må tilhøre en av `AZURE_AUTHORIZED_CLIENTS`, audience må være `AZURE_CLIENT_ID` og det må være brukt et sertifikat ved utstedelse av Access Tokenet (Ikke client_id/client_secret)

### Hente dokument
GET @ /v1/dokument/{dokumentId}
- 200 eller 404 response
- Om "Accept" Header er satt til "application/json" returners vedlegget på format
```json
    {
        "title" : "Tittel som ble satt ved lagring",
        "content" : "ey123...",
        "content_type" : "application/pdf"
    }
```
- Om en annen/ingen "Accept"-header er satt vil vedlegget returneres som oppgitt Content-Type ved lagring.

### Slette dokument
DELETE @ /v1/dokument/{dokumentId}
- 204 Response om dokumentet ble slettet
- 404 Resposne om det ikke fantes noe dokument å slette

### Lagre dokument
POST @ /v1/dokument
- 201 response med "Location" Header satt som peker på URL'en til dokumentet.
- En JSON request på samme format som henting av dokument som JSON beskrevet ovenfor.
- Returnerer også en entity med id til dokumentet
```json
{
    "id" : "eyJraWQiOiIxIiwidHlwIjoiSldUIiwiYWxnIjoibm9uZSJ9.eyJqdGkiOiJiZTRhMjM5Yy1hZDIxLTQ5OTYtOTE3MS1kNjljY2Y1OGE4YjAifQ"
}
```

### Customized Dokument ID
- Velge dokumentID selv om man ikke har noe sted å lagre den genererte
- For systembruker kan det settes en `Expires` request header som ISO8601 ZonedDateTime for når den expirer.

#### PUT @ /v1/dokument/customized/{customDokumentId}
- Samme format som ved lagring av vanlig dokument
- Må være `Content-Type` header `content_type` i request `application/json`
- Returnerer 204 og overskriver eventuell verdi som var lagret på denne id'fra før.

#### GET @ /v1/dokument/customized/{customDokumentId}
- Må sette `Accept` header til `application/json`
- Returnerer 200 om dokumentet  er funnet, 404 ellers.

## Bygge prosjektet
Krever et miljø med Docker installert for å kjøre tester.

## Azure
Display name != config `AZURE_CLIENT_ID` - Det er en UUID som varierer fra miljø til miljø.
Instansene som mellomlagrer vedlegg før søknad er sendt inn bruker Azure client med display name 'pleiepenger-mellomlagring-soknad' (Denne brukes per nå ikke ettersom lagring av vedlegg gjøres ved bruk av Login Service tokens.)
Instansene som mellomlagrer dokumenter før søknaden er journalført bruker Azure client med display name 'pleiepenger-mellomlagring-journalforing'

## S3
Instansene som mellomlagrer vedlegg før søknaden er sendt inn bruker userId 'ppd-mellomlagring-soknad' (S3 settes da opp med expiry på 1 dag)
Instansene som mellomlagrer dokumenter før søknaden er journalført bruker userId 'ppd-mellomlagring-journalforing' (S3 settes da opp uten expiry. Dokumenter slettes eksplisitt så fort de er journalført.)

## Correlation ID vs Request ID
Correlation ID blir propagert videre, og har ikke nødvendigvis sitt opphav hos konsumenten.
Request ID blir ikke propagert videre, og skal ha sitt opphav hos konsumenten.

## Alarmer
Vi bruker [nais-alerts](https://doc.nais.io/observability/alerts) for å sette opp alarmer. Disse finner man konfigurert i [nais/alerterator.yml](nais/alerterator.yml).

## Henvendelser
Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

Interne henvendelser kan sendes via Slack i kanalen #team-düsseldorf.
