// Демонстрационный скрипт: сегментация по телефону
// В реальном сценарии вместо локальной логики вызывается внешний API CRM.

def phone = (answer ?: "").replaceAll("[^0-9+]", "")
def vipPrefixes = (scriptParams?.vipPrefixes ?: ["+7999", "+37544"]).collect { String.valueOf(it) }

def segment = vipPrefixes.any { phone.startsWith(it) } ? "VIP" : "MASS"
def servicesBySegment = scriptParams?.servicesBySegment ?: [VIP:["Премиум обслуживание"], MASS:["Стандартное обслуживание"]]
def chosen = servicesBySegment[segment] ?: ["Стандартное обслуживание"]

return [
  message: "Сегмент определен: ${segment}",
  serviceNames: chosen,
  multiServicesAction: "choose",
  visitParameters: [
    customerPhone: phone,
    crmSegment: segment,
    segmentationModel: scriptMetadata?.model ?: "rule-based",
    scriptVersion: scriptVersion ?: "2.1.0"
  ]
]
