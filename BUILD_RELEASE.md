# Build release

- Run ./build-picframe.sh.
- Make sure to build the signed APK by following the instructions in the script.
- Use picframe-release.keystore with alias: picframe.
- Use default password: Xx######
- The keystore and release-keystore.properties are kept out of git. Back both up
  somewhere safe: devices only accept updates signed with this same key.
