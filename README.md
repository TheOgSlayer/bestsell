# SellGUI

`/sell` opens a GUI. Put items in, then click the green **SELL** block or close the menu (Esc) to sell.
Prices are set in `plugins/SellGUI/prices.yml`. Requires **Vault** + an economy (EssentialsX).

Sell message: `+$<amount>` (the `$` is bright green).

## Build
GitHub Actions builds the jar on every push: open the **Actions** tab -> latest run -> download the `SellGUI` artifact.
Locally: `mvn package` -> `target/SellGUI.jar`.
