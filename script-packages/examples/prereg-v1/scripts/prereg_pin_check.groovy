// Вход: answer, answers, scriptParams, scriptMetadata, scriptVersion
// Выход: Map

def pin = (answer ?: "").trim()
def whitelist = (scriptParams?.validPins ?: ["1234", "9999"]).collect { String.valueOf(it) }

def status = whitelist.contains(pin) ? "FOUND" : "NOT_FOUND"
if (status == "FOUND") {
  return [
    message: "Предзапись найдена. Продолжаем оформление.",
    serviceNames: scriptParams?.serviceNames ?: ["Консультации"],
    multiServicesAction: scriptParams?.multiServicesAction ?: "choose",
    visitParameters: [
      prebookPin: pin,
      prebookStatus: status,
      scriptId: scriptMetadata?.id ?: "prereg-pin-check",
      scriptVersion: scriptVersion ?: "1.0.0"
    ]
  ]
}

return [
  message: "Предзапись не найдена. Проверьте PIN и повторите.",
  nextQuestionId: scriptParams?.retryQuestionId ?: "bank_q_pin",
  visitParameters: [
    prebookPin: pin,
    prebookStatus: status
  ]
]
