ALTER TABLE recognitions
    DROP CONSTRAINT IF EXISTS recognitions_recognized_object_check;

ALTER TABLE recognitions
    ADD CONSTRAINT recognitions_recognized_object_check
    CHECK (recognized_object IN (
        'FLOWER', 'TREE', 'PLANT', 'SKY', 'LANDSCAPE',
        'CAT', 'DOG', 'BIRD', 'ANIMAL',
        'FOOD', 'BREAD', 'FRUIT', 'VEGETABLE', 'TOMATO', 'CARROT', 'POTATO', 'WHEAT',
        'WATER', 'RIVER', 'SEA', 'POND',
        'ROAD', 'PATH', 'PARK', 'STREET',
        'BOOK', 'NOTEBOOK', 'STUDY', 'READING', 'LECTURE', 'WRITING', 'LIBRARY',
        'LAPTOP', 'COMPUTER', 'CODING', 'PROGRAMMING', 'DESK', 'OFFICE', 'MEETING', 'WORKSPACE',
        'COFFEE', 'ROOM', 'DAILY_OBJECT', 'PERSON', 'FRIEND', 'FAMILY', 'OBJECT', 'UNKNOWN'
    ));
