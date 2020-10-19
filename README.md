# k9-mellomlagring

![](https://github.com/navikt/k9-mellomlagring/workflows/CI%20/%20CD/badge.svg)
![NAIS Alerts](https://github.com/navikt/k9-mellomlagring/workflows/Alerts/badge.svg)

Mellomlagrer vedlegg før innsending av søknad, og dokumenter før journalføring.

## API
### Tilgang
- Da login service er konfigurert som issuer, må ID-token utstedt fra Login Service på Level4 sendes som "Authorization" header (Bearer schema). Da hentes eier av dokumentet fra tokenets `sub` claim. Det er da kun denne personen som kan operere på sine dokumenter.
- Da azure er konfigurert som issuer, må Access Token sendes i "Authorization" header (Bearer schema). Da hentes eier av dokumentet fra request body `eiers_fodselsnummer`.

### Login Service
ID-Tokenet må være på Level4

### Azure
Access Tokenet må tilhøre en av `AZURE_AUTHORIZED_CLIENTS`, audience må være `AZURE_CLIENT_ID` og det må være brukt et sertifikat ved utstedelse av Access Tokenet (Ikke client_id/client_secret)

### Hente dokument
POST @ /v1/dokument/{dokumentId}
```json
{
  "eiers_fødselsnummer": "12345678910"
}
```
- 200 eller 404 response
- Om "Accept" Header er satt til "application/json" returners vedlegget på format
```json
    {
        "title" : "Tittel som ble satt ved lagring",
        "content" : "ey123...",
        "content_type" : "application/pdf",
        "eier": {
                "eiers_fødselsnummer": "12345678910"
            }
    }
```
- Om en annen/ingen "Accept"-header er satt vil vedlegget returneres som oppgitt Content-Type ved lagring.

### Slette dokument
DELETE @ /v1/dokument/{dokumentId}
```json
{
  "eiers_fødselsnummer": "12345678910"
}
```
- 204 Response om dokumentet ble slettet
- 404 Resposne om det ikke fantes noe dokument å slette

### Lagre dokument
Dokumenter som blir mellomlagret med en `selvbetjeing-idtoken` vil automatisk få en custom date-time på metadata til dokumentet.
Denne vil bli slettet etter 24t dersom det ikke gjøres et put-kall for å persistere den. Det kallet må da gjøres av en azure(v1 eller v2) issuer.
Dersom det er en azure issuer som lagrer dokumentet, vil den leve til den blir slettet manuelt av tjenesten som lagde den.
POST @ /v1/dokument
```json
{
    "title": "Tittel som ble satt ved lagring",
    "content": "ey123...",
    "content_type": "image/png",
    "eier": {
        "eiers_fødselsnummer": "12345678910"
    }
}
```
- 201 response med "Location" Header satt som peker på URL'en til dokumentet.
- En JSON request på samme format som henting av dokument som JSON beskrevet ovenfor.
- Returnerer også en entity med id til dokumentet
```json
{
    "id" : "eyJraWQiOiIxIiwidHlwIjoiSldUIiwiYWxnIjoibm9uZSJ9.eyJqdGkiOiJiZTRhMjM5Yy1hZDIxLTQ5OTYtOTE3MS1kNjljY2Y1OGE4YjAifQ"
}
```

### Persistere dokument
Dokumenter som opprettes av en `loginservice issuer (selvbetjening-idtoken)`, vil få en levetid på 24t før den slettes.
Dersom det ønskes å øke levetiden på dokumentet, må følge kall bli utført av en `azure issuer`.
PUT @ /v1/dokument/{dokumentId}
```json
{
  "eiers_fødselsnummer": "12345678910"
}
```

### Customized Dokument ID
- Velge dokumentID selv om man ikke har noe sted å lagre den genererte

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
