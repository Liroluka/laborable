-- ISO 3166-2:ES autonomous communities and autonomous cities
INSERT INTO region (code, name) VALUES
    ('AN', 'Andalucía'), ('AR', 'Aragón'), ('AS', 'Asturias'), ('CB', 'Cantabria'),
    ('CE', 'Ceuta'), ('CL', 'Castilla y León'), ('CM', 'Castilla-La Mancha'), ('CN', 'Canarias'),
    ('CT', 'Cataluña'), ('EX', 'Extremadura'), ('GA', 'Galicia'), ('IB', 'Illes Balears'),
    ('MC', 'Región de Murcia'), ('MD', 'Comunidad de Madrid'), ('ML', 'Melilla'),
    ('NC', 'Navarra'), ('PV', 'País Vasco'), ('RI', 'La Rioja'), ('VC', 'Comunitat Valenciana');

-- INE province codes (first two digits of a municipality INE code) mapped to regions
INSERT INTO province (code, name, region_code) VALUES
    ('01', 'Araba/Álava', 'PV'), ('02', 'Albacete', 'CM'), ('03', 'Alicante/Alacant', 'VC'),
    ('04', 'Almería', 'AN'), ('05', 'Ávila', 'CL'), ('06', 'Badajoz', 'EX'),
    ('07', 'Illes Balears', 'IB'), ('08', 'Barcelona', 'CT'), ('09', 'Burgos', 'CL'),
    ('10', 'Cáceres', 'EX'), ('11', 'Cádiz', 'AN'), ('12', 'Castellón/Castelló', 'VC'),
    ('13', 'Ciudad Real', 'CM'), ('14', 'Córdoba', 'AN'), ('15', 'A Coruña', 'GA'),
    ('16', 'Cuenca', 'CM'), ('17', 'Girona', 'CT'), ('18', 'Granada', 'AN'),
    ('19', 'Guadalajara', 'CM'), ('20', 'Gipuzkoa', 'PV'), ('21', 'Huelva', 'AN'),
    ('22', 'Huesca', 'AR'), ('23', 'Jaén', 'AN'), ('24', 'León', 'CL'),
    ('25', 'Lleida', 'CT'), ('26', 'La Rioja', 'RI'), ('27', 'Lugo', 'GA'),
    ('28', 'Madrid', 'MD'), ('29', 'Málaga', 'AN'), ('30', 'Murcia', 'MC'),
    ('31', 'Navarra', 'NC'), ('32', 'Ourense', 'GA'), ('33', 'Asturias', 'AS'),
    ('34', 'Palencia', 'CL'), ('35', 'Las Palmas', 'CN'), ('36', 'Pontevedra', 'GA'),
    ('37', 'Salamanca', 'CL'), ('38', 'Santa Cruz de Tenerife', 'CN'), ('39', 'Cantabria', 'CB'),
    ('40', 'Segovia', 'CL'), ('41', 'Sevilla', 'AN'), ('42', 'Soria', 'CL'),
    ('43', 'Tarragona', 'CT'), ('44', 'Teruel', 'AR'), ('45', 'Toledo', 'CM'),
    ('46', 'Valencia/València', 'VC'), ('47', 'Valladolid', 'CL'), ('48', 'Bizkaia', 'PV'),
    ('49', 'Zamora', 'CL'), ('50', 'Zaragoza', 'AR'), ('51', 'Ceuta', 'CE'), ('52', 'Melilla', 'ML');
