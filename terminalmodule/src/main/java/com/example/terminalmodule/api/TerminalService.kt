package com.example.terminalmodule.api

import com.example.terminalmodule.model.DeviceType

/** Point d'entrée unique vers le matériel. Implémenté par la couche L3 de chaque flavor. */
interface TerminalServices {
    val device: DeviceType
    val printer: PrinterService
    val cardReader: CardReaderService
    val scanner: ScannerService
    val pinPad: PinPadService
}
