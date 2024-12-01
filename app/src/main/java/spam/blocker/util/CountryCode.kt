package spam.blocker.util

import android.content.Context
import android.telephony.TelephonyManager
import java.util.regex.Pattern

object CountryCode {

    // This function parse the `cc` and `phone` from the full number
    // return value:
    //   Triple(succeeded, cc, domestic)
    // e.g.:
    //   12223334444 will be parsed to
    //     cc = 1, domestic = 2223334444
    fun parseCcDomestic(fullNumber: String): Triple<Boolean, String, String> {
        val pattern = Pattern.compile("^([17]|2[07]|3[0123469]|4[013456789]|5[12345678]|6[0123456]|8[1246]|9[0123458]|\\d{3})\\d*?(\\d{4,6})$")
        val matcher = pattern.matcher(fullNumber)

        if (!matcher.matches()) {
            return Triple(false, "", "")
        }

        val cc = matcher.group(1)
        val phone = fullNumber.substring(cc.length)

        return Triple(true, cc, phone)
    }


    private fun detectNetworkCountry(ctx: Context): String? {
        return try {
            val telephonyManager = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            telephonyManager.networkCountryIso.uppercase()
        } catch (_: Exception) {
            null
        }
    }
    private fun detectLocaleCountry(context: Context): String? {
        return try {
            context.resources.configuration.locales[0].country.uppercase()
        } catch (_: Exception) {
            null
        }
    }

    fun current(ctx: Context): Int? {

        val cc = detectNetworkCountry(ctx)
            ?: detectLocaleCountry(ctx)
            ?: ""

        // https://countrycode.org/
        return when (cc) {
            "AF", "AFG" -> 93                 //  Afghanistan
            "AL", "ALB" -> 355                //  Albania
            "DZ", "DZA" -> 213                //  Algeria
            "AS", "ASM" -> 1684              //  American Samoa
            "AD", "AND" -> 376                //  Andorra
            "AO", "AGO" -> 244                //  Angola
            "AI", "AIA" -> 1264              //  Anguilla
            "AQ", "ATA" -> 672                //  Antarctica
            "AG", "ATG" -> 1268              //  Antigua and Barbuda
            "AR", "ARG" -> 54                 //  Argentina
            "AM", "ARM" -> 374                //  Armenia
            "AW", "ABW" -> 297                //  Aruba
            "AU", "AUS" -> 61                 //  Australia
            "AT", "AUT" -> 43                 //  Austria
            "AZ", "AZE" -> 994                //  Azerbaijan
            "BS", "BHS" -> 1243              //  Bahamas
            "BH", "BHR" -> 973                //  Bahrain
            "BD", "BGD" -> 880                //  Bangladesh
            "BB", "BRB" -> 1246              //  Barbados
            "BY", "BLR" -> 375                //  Belarus
            "BE", "BEL" -> 32                 //  Belgium
            "BZ", "BLZ" -> 501                //  Belize
            "BJ", "BEN" -> 229                //  Benin
            "BM", "BMU" -> 1441              //  Bermuda
            "BT", "BTN" -> 975                //  Bhutan
            "BO", "BOL" -> 591                //  Bolivia
            "BA", "BIH" -> 387                //  Bosnia and Herzegovina
            "BW", "BWA" -> 267                //  Botswana
            "BR", "BRA" -> 55                 //  Brazil
            "IO", "IOT" -> 246                //  British Indian Ocean Territory
            "VG", "VGB" -> 1284              //  British Virgin Islands
            "BN", "BRN" -> 673                //  Brunei
            "BG", "BGR" -> 359                //  Bulgaria
            "BF", "BFA" -> 226                //  Burkina Faso
            "BI", "BDI" -> 257                //  Burundi
            "KH", "KHM" -> 855                //  Cambodia
            "CM", "CMR" -> 237                //  Cameroon
            "CA", "CAN" -> 1                  //  Canada
            "CV", "CPV" -> 238                //  Cape Verde
            "KY", "CYM" -> 1345              //  Cayman Islands
            "CF", "CAF" -> 236                //  Central African Republic
            "TD", "TCD" -> 235                //  Chad
            "CL", "CHL" -> 56                 //  Chile
            "CN", "CHN" -> 86                 //  China
            "CX", "CXR" -> 61                 //  Christmas Island
            "CC", "CCK" -> 61                 //  Cocos Islands
            "CO", "COL" -> 57                 //  Colombia
            "KM", "COM" -> 269                //  Comoros
            "CK", "COK" -> 682                //  Cook Islands
            "CR", "CRI" -> 506                //  Costa Rica
            "HR", "HRV" -> 385                //  Croatia
            "CU", "CUB" -> 53                 //  Cuba
            "CW", "CUW" -> 599                //  Curacao
            "CY", "CYP" -> 357                //  Cyprus
            "CZ", "CZE" -> 420                //  Czech Republic
            "CD", "COD" -> 243                //  Democratic Republic of the Congo
            "DK", "DNK" -> 45                 //  Denmark
            "DJ", "DJI" -> 253                //  Djibouti
            "DM", "DMA" -> 1767              //  Dominica
//    "DO" , "DOM"   ->  1-809, 1-829, 1-849//  Dominican Republic
            "TL", "TLS" -> 670                //  East Timor
            "EC", "ECU" -> 593                //  Ecuador
            "EG", "EGY" -> 20                 //  Egypt
            "SV", "SLV" -> 503                //  El Salvador
            "GQ", "GNQ" -> 240                //  Equatorial Guinea
            "ER", "ERI" -> 291                //  Eritrea
            "EE", "EST" -> 372                //  Estonia
            "ET", "ETH" -> 251                //  Ethiopia
            "FK", "FLK" -> 500                //  Falkland Islands
            "FO", "FRO" -> 298                //  Faroe Islands
            "FJ", "FJI" -> 679                //  Fiji
            "FI", "FIN" -> 358                //  Finland
            "FR", "FRA" -> 33                 //  France
            "PF", "PYF" -> 689                //  French Polynesia
            "GA", "GAB" -> 241                //  Gabon
            "GM", "GMB" -> 220                //  Gambia
            "GE", "GEO" -> 995                //  Georgia
            "DE", "DEU" -> 49                 //  Germany
            "GH", "GHA" -> 233                //  Ghana
            "GI", "GIB" -> 350                //  Gibraltar
            "GR", "GRC" -> 30                 //  Greece
            "GL", "GRL" -> 299                //  Greenland
            "GD", "GRD" -> 1473              //  Grenada
            "GU", "GUM" -> 1671              //  Guam
            "GT", "GTM" -> 502                //  Guatemala
            "GG", "GGY" -> 441481            //  Guernsey
            "GN", "GIN" -> 224                //  Guinea
            "GW", "GNB" -> 245                //  Guinea-Bissau
            "GY", "GUY" -> 592                //  Guyana
            "HT", "HTI" -> 509                //  Haiti
            "HN", "HND" -> 504                //  Honduras
            "HK", "HKG" -> 852                //  Hong Kong
            "HU", "HUN" -> 36                 //  Hungary
            "IS", "ISL" -> 354                //  Iceland
            "IN", "IND" -> 91                 //  India
            "ID", "IDN" -> 62                 //  Indonesia
            "IR", "IRN" -> 98                 //  Iran
            "IQ", "IRQ" -> 964                //  Iraq
            "IE", "IRL" -> 353                //  Ireland
            "IM", "IMN" -> 441624            //  Isle of Man
            "IL", "ISR" -> 972                //  Israel
            "IT", "ITA" -> 39                 //  Italy
            "CI", "CIV" -> 225                //  Ivory Coast
            "JM", "JAM" -> 1876              //  Jamaica
            "JP", "JPN" -> 81                 //  Japan
            "JE", "JEY" -> 441534            //  Jersey
            "JO", "JOR" -> 962                //  Jordan
            "KZ", "KAZ" -> 7                  //  Kazakhstan
            "KE", "KEN" -> 254                //  Kenya
            "KI", "KIR" -> 686                //  Kiribati
            "XK", "XKX" -> 383                //  Kosovo
            "KW", "KWT" -> 965                //  Kuwait
            "KG", "KGZ" -> 996                //  Kyrgyzstan
            "LA", "LAO" -> 856                //  Laos
            "LV", "LVA" -> 371                //  Latvia
            "LB", "LBN" -> 961                //  Lebanon
            "LS", "LSO" -> 266                //  Lesotho
            "LR", "LBR" -> 231                //  Liberia
            "LY", "LBY" -> 218                //  Libya
            "LI", "LIE" -> 423                //  Liechtenstein
            "LT", "LTU" -> 370                //  Lithuania
            "LU", "LUX" -> 352                //  Luxembourg
            "MO", "MAC" -> 853                //  Macau
            "MK", "MKD" -> 389                //  Macedonia
            "MG", "MDG" -> 261                //  Madagascar
            "MW", "MWI" -> 265                //  Malawi
            "MY", "MYS" -> 60                 //  Malaysia
            "MV", "MDV" -> 960                //  Maldives
            "ML", "MLI" -> 223                //  Mali
            "MT", "MLT" -> 356                //  Malta
            "MH", "MHL" -> 692                //  Marshall Islands
            "MR", "MRT" -> 222                //  Mauritania
            "MU", "MUS" -> 230                //  Mauritius
            "YT", "MYT" -> 262                //  Mayotte
            "MX", "MEX" -> 52                 //  Mexico
            "FM", "FSM" -> 691                //  Micronesia
            "MD", "MDA" -> 373                //  Moldova
            "MC", "MCO" -> 377                //  Monaco
            "MN", "MNG" -> 976                //  Mongolia
            "ME", "MNE" -> 382                //  Montenegro
            "MS", "MSR" -> 1664              //  Montserrat
            "MA", "MAR" -> 212                //  Morocco
            "MZ", "MOZ" -> 258                //  Mozambique
            "MM", "MMR" -> 95                 //  Myanmar
            "NA", "NAM" -> 264                //  Namibia
            "NR", "NRU" -> 674                //  Nauru
            "NP", "NPL" -> 977                //  Nepal
            "NL", "NLD" -> 31                 //  Netherlands
            "AN", "ANT" -> 599                //  Netherlands Antilles
            "NC", "NCL" -> 687                //  New Caledonia
            "NZ", "NZL" -> 64                 //  New Zealand
            "NI", "NIC" -> 505                //  Nicaragua
            "NE", "NER" -> 227                //  Niger
            "NG", "NGA" -> 234                //  Nigeria
            "NU", "NIU" -> 683                //  Niue
            "KP", "PRK" -> 850                //  North Korea
            "MP", "MNP" -> 1670              //  Northern Mariana Islands
            "NO", "NOR" -> 47                 //  Norway
            "OM", "OMN" -> 968                //  Oman
            "PK", "PAK" -> 92                 //  Pakistan
            "PW", "PLW" -> 680                //  Palau
            "PS", "PSE" -> 970                //  Palestine
            "PA", "PAN" -> 507                //  Panama
            "PG", "PNG" -> 675                //  Papua New Guinea
            "PY", "PRY" -> 595                //  Paraguay
            "PE", "PER" -> 51                 //  Peru
            "PH", "PHL" -> 63                 //  Philippines
            "PN", "PCN" -> 64                 //  Pitcairn
            "PL", "POL" -> 48                 //  Poland
            "PT", "PRT" -> 351                //  Portugal
//    "PR" , "PRI"   ->  1-787, 1-939       //  Puerto Rico
            "QA", "QAT" -> 974                //  Qatar
            "CG", "COG" -> 242                //  Republic of the Congo
            "RE", "REU" -> 262                //  Reunion
            "RO", "ROU" -> 40                 //  Romania
            "RU", "RUS" -> 7                  //  Russia
            "RW", "RWA" -> 250                //  Rwanda
            "BL", "BLM" -> 590                //  Saint Barthelemy
            "SH", "SHN" -> 290                //  Saint Helena
            "KN", "KNA" -> 1869              //  Saint Kitts and Nevis
            "LC", "LCA" -> 1758              //  Saint Lucia
            "MF", "MAF" -> 590                //  Saint Martin
            "PM", "SPM" -> 508                //  Saint Pierre and Miquelon
            "VC", "VCT" -> 1784              //  Saint Vincent and the Grenadines
            "WS", "WSM" -> 685                //  Samoa
            "SM", "SMR" -> 378                //  San Marino
            "ST", "STP" -> 239                //  Sao Tome and Principe
            "SA", "SAU" -> 966                //  Saudi Arabia
            "SN", "SEN" -> 221                //  Senegal
            "RS", "SRB" -> 381                //  Serbia
            "SC", "SYC" -> 248                //  Seychelles
            "SL", "SLE" -> 232                //  Sierra Leone
            "SG", "SGP" -> 65                 //  Singapore
            "SX", "SXM" -> 1721              //  Sint Maarten
            "SK", "SVK" -> 421                //  Slovakia
            "SI", "SVN" -> 386                //  Slovenia
            "SB", "SLB" -> 677                //  Solomon Islands
            "SO", "SOM" -> 252                //  Somalia
            "ZA", "ZAF" -> 27                 //  South Africa
            "KR", "KOR" -> 82                 //  South Korea
            "SS", "SSD" -> 211                //  South Sudan
            "ES", "ESP" -> 34                 //  Spain
            "LK", "LKA" -> 94                 //  Sri Lanka
            "SD", "SDN" -> 249                //  Sudan
            "SR", "SUR" -> 597                //  Suriname
            "SJ", "SJM" -> 47                 //  Svalbard and Jan Mayen
            "SZ", "SWZ" -> 268                //  Swaziland
            "SE", "SWE" -> 46                 //  Sweden
            "CH", "CHE" -> 41                 //  Switzerland
            "SY", "SYR" -> 963                //  Syria
            "TW", "TWN" -> 886                //  Taiwan
            "TJ", "TJK" -> 992                //  Tajikistan
            "TZ", "TZA" -> 255                //  Tanzania
            "TH", "THA" -> 66                 //  Thailand
            "TG", "TGO" -> 228                //  Togo
            "TK", "TKL" -> 690                //  Tokelau
            "TO", "TON" -> 676                //  Tonga
            "TT", "TTO" -> 1868              //  Trinidad and Tobago
            "TN", "TUN" -> 216                //  Tunisia
            "TR", "TUR" -> 90                 //  Turkey
            "TM", "TKM" -> 993                //  Turkmenistan
            "TC", "TCA" -> 1649              //  Turks and Caicos Islands
            "TV", "TUV" -> 688                //  Tuvalu
            "VI", "VIR" -> 1340              //  U.S. Virgin Islands
            "UG", "UGA" -> 256                //  Uganda
            "UA", "UKR" -> 380                //  Ukraine
            "AE", "ARE" -> 971                //  United Arab Emirates
            "GB", "GBR" -> 44                 //  United Kingdom
            "US", "USA" -> 1                  //  United States
            "UY", "URY" -> 598                //  Uruguay
            "UZ", "UZB" -> 998                //  Uzbekistan
            "VU", "VUT" -> 678                //  Vanuatu
            "VA", "VAT" -> 379                //  Vatican
            "VE", "VEN" -> 58                 //  Venezuela
            "VN", "VNM" -> 84                 //  Vietnam
            "WF", "WLF" -> 681                //  Wallis and Futuna
            "EH", "ESH" -> 212                //  Western Sahara
            "YE", "YEM" -> 967                //  Yemen
            "ZM", "ZMB" -> 260                //  Zambia
            "ZW", "ZWE" -> 263                //  Zimbabwe
            else -> null
        }
    }
}

