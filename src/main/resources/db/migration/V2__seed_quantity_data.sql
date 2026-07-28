-- =========================================================
-- V2 : données de seed pour les référentiels quantity_types
-- et quantity_units (12 lignes d'unités au total).
-- ON CONFLICT DO NOTHING : relancer ce script (ou une future
-- migration "repeatable" équivalente) ne crée pas de doublons.
-- =========================================================

INSERT INTO quantity_types (code, label) VALUES
    ('weight', 'Poids'),
    ('liquid', 'Liquide'),
    ('unit', 'Unité')
ON CONFLICT (code) DO NOTHING;

-- Unités de poids : la référence (is_base_unit) est le kilogramme,
-- les facteurs de conversion expriment chaque unité en kilogrammes.
INSERT INTO quantity_units (quantity_type_id, code, label, conversion_factor, is_base_unit, sort_order)
SELECT id, 'kg', 'Kilogramme', 1, TRUE, 1 FROM quantity_types WHERE code = 'weight'
UNION ALL
SELECT id, 'hg', 'Hectogramme', 0.1, FALSE, 2 FROM quantity_types WHERE code = 'weight'
UNION ALL
SELECT id, 'dag', 'Décagramme', 0.01, FALSE, 3 FROM quantity_types WHERE code = 'weight'
UNION ALL
SELECT id, 'g', 'Gramme', 0.001, FALSE, 4 FROM quantity_types WHERE code = 'weight'
UNION ALL
SELECT id, 'dg', 'Décigramme', 0.0001, FALSE, 5 FROM quantity_types WHERE code = 'weight'
UNION ALL
SELECT id, 'cg', 'Centigramme', 0.00001, FALSE, 6 FROM quantity_types WHERE code = 'weight'
UNION ALL
SELECT id, 'mg', 'Milligramme', 0.000001, FALSE, 7 FROM quantity_types WHERE code = 'weight'
UNION ALL
-- Unités de liquide : la référence est le litre, les facteurs
-- de conversion expriment chaque unité en litres.
SELECT id, 'L', 'Litre', 1, TRUE, 1 FROM quantity_types WHERE code = 'liquid'
UNION ALL
SELECT id, 'dL', 'Décilitre', 0.1, FALSE, 2 FROM quantity_types WHERE code = 'liquid'
UNION ALL
SELECT id, 'cL', 'Centilitre', 0.01, FALSE, 3 FROM quantity_types WHERE code = 'liquid'
UNION ALL
SELECT id, 'mL', 'Millilitre', 0.001, FALSE, 4 FROM quantity_types WHERE code = 'liquid'
UNION ALL
-- Type "unit" : une seule unité, sans sous-multiple.
SELECT id, 'unit', 'Unité', 1, TRUE, 1 FROM quantity_types WHERE code = 'unit'
ON CONFLICT (quantity_type_id, code) DO NOTHING;

