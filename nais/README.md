# nais

## Tilgang

### Requests fra GCP
- Bruker service-discovery og tillater requester fra alle applikasjoner som ligger under `accessPolicy.inbound.rules` i naiserator.yml.
  Applikasjoner som kaller denne tjenesten må legge k9-mellomlagring inn i deres `accessPolicy.outbound.rules` 

