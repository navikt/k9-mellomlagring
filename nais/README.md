# nais

## Merk!
For tjenesten som kjører i FSS heter denne applikasjonen `pleiepenger-mellomlagring-journalforing` i Azure.

For tjenesten om kjører i SBS er ikke applikasjonen i Azure da `login-service` brukes.

## Tilgang
### Tjenesten i FSS
- Tillater requester fra alle applikasjoner som ligger under `preauthorizedapplications` i `aad-iac` under `pleiepenger-mellomlagring-journalforing`.

## Tjenesten i SBS
- Trenger ingen tilganger, bruker innlogget brukers ID-token gjennom `login-service`.