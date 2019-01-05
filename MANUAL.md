# Installation

- download monika.jar
- set `MONIKA_HOME` in `/etc/environment` to an empty directory
- `crontab -e` and add `java -jar monika.jar --server`
- (optional) set `http_proxy` and `https_proxy` in `/etc/environment`

# Basic Usage

- `java -jar monika.jar` this will start a new monika client
- `hi` this will display help information

# Examples

## Lock for next day

- start monika client
- `make passwords`
- `lock next day`

