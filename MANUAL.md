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
- `mk-pwds`: generates passwords for the next 100 days
- `ls-pwds`: prints out the generated passwords
- `locknx`: changes the system password to that for the next day

## Profile Management

- `queue <profile> <time>`: change to the specified profile in 1 minute
    - once the profile has taken effect, the user will no longer have ROOT access (removed wheel group)
    - any restricted programs will be terminated
