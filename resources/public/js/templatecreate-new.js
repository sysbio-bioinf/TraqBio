(function() {
    var stepIdCounter = 0;
    var moduleIdCounter = 0;


    function moveButton(direction) {
        $button = $("<button>", {
            class: "btn btn-default btn-row-" + direction,
            type: "button"
        });
        $button.append($("<span>", {
            class: "glyphicon glyphicon-chevron-" + direction
        }).prop("outerHTML"));
        return $button.prop("outerHTML");
    }

    function deleteButton() {
        $button = $("<button>", {
            class: "btn btn-default btn-delete-row",
            type: "button"
        });
        $button.append($("<span>", {
            class: "glyphicon glyphicon-trash"
        }).prop("outerHTML"));
        return $button.prop("outerHTML");
    }

    function renderType(data, type, row, meta) {
        var $input = $("<input>",{
            "value": data,
            "type": "text",
            "class": "form-control text-input",
            "width": "100%",
            "data-attribute": "type"});
        return $input.prop("outerHTML");
    }

    function renderDescription(data, type, row, meta) {
        var $textarea = $("<textarea>", {
            "text": data,
            "type": "text",
            "class": "form-control text-input",
             "rows": 1,
             "width": "100%",
             "data-attribute": "description"});
        return $textarea.prop("outerHTML");
    }

    function renderMove(data, type, row, meta) {
        var $div = $("<div>");
        $div.append( moveButton("up") ).append( moveButton("down") );
        return $div.prop("outerHTML");
    }

    function renderDelete(data, type, row, meta) {
        return deleteButton();
    }

    function moveRow($table, stepsChangedHandler, row, direction) {
        var rowCount = $table.data().length;

        var tableRow = $table.row(row);
        var rowIndex = tableRow.index();
        var otherRowIndex = rowIndex + direction;

        if( rowIndex == 0 && direction == -1)
            return;
        if( rowIndex == rowCount - 1 && direction == 1)
            return;

        var rowData = $table.row(rowIndex).data();
        var otherRowData = $table.row(otherRowIndex).data();

        $table.row(rowIndex).data(otherRowData);
        $table.row(otherRowIndex).data(rowData);

        $table.rows().draw();
        stepsChangedHandler( $table );
    }

    function deleteRow($table, stepsChangedHandler, row) {
        $table.row(row).remove().draw();
        stepsChangedHandler( $table );
    }

    function onRowCreated($templateStepsTable, stepsChangedHandler, row, data) {
        $(row).off('change', ".text-input").on('change', ".text-input", function() { templateStepsBinding($templateStepsTable, stepsChangedHandler, $(this), row); });
        $(row).off('click', '.btn-row-up').on('click', '.btn-row-up', function() { moveRow($templateStepsTable, stepsChangedHandler, row, -1); });
        $(row).off('click', '.btn-row-down').on('click', '.btn-row-down', function() { moveRow($templateStepsTable, stepsChangedHandler, row, +1); });
        $(row).off('click', '.btn-delete-row').on('click', '.btn-delete-row', function() { deleteRow($templateStepsTable, stepsChangedHandler, row); });
    }

    function templateStepsBinding($table, stepsChangedHandler, $control, row) {
        var rowdata = $table.row( row ).data();
        var attribute = $control.data().attribute;
        rowdata[ attribute ] = $control.val();
        $table.row( row ).data( rowdata ).draw();

        if( attribute == 'type') {
            stepsChangedHandler( $table );
        }
    }

    function tableData($table) {
        var data = $table.rows().data();
        var rowCount = data.length;
        var rowData = [];
        for(i = 0; i < rowCount; i++) {
            rowData[i] = data[i];
        }
        return rowData;
    }

    var successTemplate = '<div class="alert alert-success"><% message %></div>';
    var errorTemplate = '<div class="alert alert-danger"><% message %></div>';

    function onTemplateCreationFailed(msg) {
        var alert;
        if (msg.hasOwnProperty("responseJSON"))  {
            alert = {message: "Failure: " + msg.responseJSON.error };
        }
        else if (msg.hasOwnProperty("responseText")) {
            alert = {message: "Failure: " + msg.responseText };
        }
        else {
            alert = {message: "Unknown reason."};
        }

        var content = $('#after-creation-content');
        content.html(Mustache.render(errorTemplate, alert));
        $('.after-creation')
            .modal()
            .on('hidden.bs.modal', function (e) {
                window.location = serverRoot + '/';
            });
    }

    function onTemplateCreationSucceeded(msg){
        var alert = { message: "Success" };
        var content = $('#after-creation-content');
        content.before(Mustache.render(successTemplate, alert));

        $('.after-creation')
            .modal()
            .on('hidden.bs.modal', function (e) {
                window.location = serverRoot + '/';
            });
    }

    function createTemplate($templateNameControl, $templateDescriptionControl, $templateStepsTable, $textModulesTable) {

        var formErrors = $('#templateData').validator('validate').find('.has-error');
        if (formErrors.length > 0) {
            $('html, body').animate({
                scrollTop: formErrors[0].offsetTop
            }, 300);
        }
        else{
            var templateData = {
                name: $templateNameControl.val(),
                description: $templateDescriptionControl.val(),
                templatesteps: tableData($templateStepsTable),
                textmodules: $.map( tableData($textModulesTable), function(row) {
                    row.step = parseInt( row.step );
                    return row;
                })
            };

            $.ajax({
                url: serverRoot + '/template/create',
                type: 'POST',
                contentType: "application/json; charset=utf-8",
                data: JSON.stringify( templateData ),
                success: onTemplateCreationSucceeded,
                error: onTemplateCreationFailed
            });
        }
    }

    function onTemplateLoaded($templateNameControl, $templateDescriptionControl, $templateStepsTable, $textModulesTable, stepsChangedHandler, data) {
        $templateNameControl.val( data.name );
        $templateDescriptionControl.val( data.description );

        var maxId = 0;
        $.each(data.templatesteps, function( index, step ) {
            maxId = Math.max( step.id, maxId );
            data.templatesteps[index].sequence = -1;
        });
        stepIdCounter = maxId + 1;

        $templateStepsTable.clear().rows.add(data.templatesteps).draw();

        var maxModuleId = 0;
        $.each(data.textmodules, function( index, module ) {
            maxModuleId = Math.max( module.id, maxModuleId );
        });
        moduleIdCounter = maxModuleId + 1;

        $textModulesTable.clear().rows.add(data.textmodules).draw();

        stepsChangedHandler($templateStepsTable);
    }

    function addEmptyRow($templateStepsTable) {
        var $data = $templateStepsTable.rows().data();
        var rowCount = $data.length;
        $templateStepsTable.rows.add([{id: stepIdCounter++, type: "", description: "", sequence: -1}]).draw();
    }


    function addEmptyModule($textModuleTable) {
        var $data = $textModuleTable.rows().data();
        var rowCount = $data.length;
        $textModuleTable.rows.add([{id: moduleIdCounter++, step: -1, name: "", text: ""}]).draw();
    }

    function getStepNamesToIds($templateStepsTable) {
        var namesToIds = {};

        $.each( tableData($templateStepsTable), function(index, row) {
            namesToIds[ row.type ] = row.id;
        });

        return namesToIds;
    }

    function stepNameOptions(stepNamesToIds) {
        return $.map( stepNamesToIds, function(id, name) {
            var option = document.createElement('option');
            option.value = id;
            option.text = name;
            return option;
        });
    }

    function updateDropdown(dropdown, stepNamesToIds) {
        // remember selected option
        var selectedOption = $(dropdown).children('option:selected');
        var selectedId = selectedOption.val();

        // delete all except choose step
        $(dropdown).children('option').slice(2).remove();

        // add an option for each step name
        $.each( stepNameOptions(stepNamesToIds), function(index, option) {
            dropdown.options.add( option );
        });

        // reset selected option, if still present
        $.each( $(dropdown).children('option'), function(index, option) {
            if( $(option).val() == selectedId )
                $(option).prop('selected', true);
        });
    }

    function onStepsChanged($textModulesTableDOM, $templateStepsTable) {
        var stepNamesToIds = getStepNamesToIds($templateStepsTable);

        $textModulesTableDOM.find("select").each(function(dontcare, dropdown) {
            updateDropdown( dropdown, stepNamesToIds );
        });
    }

    function renderModuleStep($templateStepsTable, data, type, row, meta) {
        var $select = $("<select>", {
            "class": "form-control data-input",
            "width": "100%",
            "data-attribute": "step"});

        $select.append( "<option value=\"-1\"" + ( data == -1 ? "selected" : "" ) + ">Choose step</option>" );
        // add current step names
        var options = stepNameOptions( getStepNamesToIds( $templateStepsTable ) );
        $.each( options, function(index, option) {
            if( $(option).val() == data ) {
                $(option).attr('selected', true);
            }

            $select.append( $(option).prop("outerHTML") );
        });
        return $select.prop("outerHTML");
    }

    function renderModuleName(data, type, row, meta) {
        var $input = $("<input>",{
                    "value": data,
                    "type": "text",
                    "class": "form-control data-input",
                    "width": "100%",
                    "data-attribute": "name"});
        return $input.prop("outerHTML");
    }

    function renderModuleText(data, type, row, meta) {
        var $textarea = $("<textarea>", {
            "text": data,
            "type": "text",
            "class": "form-control data-input",
             "rows": 1,
             "width": "100%",
             "data-attribute": "text"});
        return $textarea.prop("outerHTML");
    }

    function onTextModuleRowCreated($textModulesTable, row, data) {
        $(row).off('change', ".data-input").on('change', ".data-input", function() { moduleTextBinding($textModulesTable, $(this), row); });
        $(row).off('click', '.btn-delete-row').on('click', '.btn-delete-row', function() { deleteRow($textModulesTable, row); });
    }

    function moduleTextBinding($table, $control, row) {
        var rowdata = $table.row( row ).data();
        rowdata[ $control.data().attribute ] = $control.val();
        $table.row( row ).data( rowdata ).draw();
    }

    $(document).ready(function() {
        var $templateNameControl = $('#templateName');
        var $templateDescriptionControl = $('#templateDescription');

        var $templateStepsTableDOM = $('#templateStepsTable');
        var $textModulesTableDOM = $('#textModulesTable');

        var stepsChangedHandler = function(templateStepsTable) { onStepsChanged($textModulesTableDOM, templateStepsTable); };

        var $templateStepsTable = $templateStepsTableDOM.DataTable( {
            ordering: false,
            paging: false,
            searching: false,
            stripeClasses: [],
            bInfo: false,
            language: {"emptyTable": "No template steps defined, yet."},
            data: [],
            rowCallback: function(row, data) { onRowCreated($templateStepsTable, stepsChangedHandler, row, data); },
            columns: [
                { title: "Type", data: "type", width: "25%", render: renderType},
                { title: "Description", data: "description", render: renderDescription},
                { title: "Move", defaultContent: "", width: "80px", render: renderMove},
                { title: "Delete", defaultContent: "", width: "40px", render: renderDelete}
            ]
        } );

        $('.btn-add-step').off('click').on('click', function(e) { addEmptyRow($templateStepsTable); });

        var $textModulesTable = $textModulesTableDOM.DataTable( {
            ordering: false,
            paging: false,
            searching: false,
            stripeClasses: [],
            bInfo: false,
            language: {"emptyTable": "No text modules defined, yet."},
            data: [],
            rowCallback: function(row, data) { onTextModuleRowCreated($textModulesTable, row, data); },
            columns: [
                { title: "Step", data: "step", width: "20%",
                  render: function(data, type, row, meta) { return renderModuleStep($templateStepsTable, data, type, row, meta); } },
                { title: "Name", data: "name", width: "20%", render: renderModuleName },
                { title: "Text", data: "text", render: renderModuleText },
                { title: "Delete", defaultContent: "", width: "40px", render: renderDelete}
            ]
        } );

        $('.btn-add-module').off('click').on('click', function(e) { addEmptyModule($textModulesTable); });


        // setup template selection
        $('#selectTemplate').on('change', function(e) {
            var id = parseInt(e.currentTarget.value , 10);
            if (id > 0) {
                $.ajax({
                    url: serverRoot + '/template/' + id,
                    type: 'GET',
                    contentType: "application/json; charset=utf-8",
                    success: function (data) {
                        onTemplateLoaded($templateNameControl, $templateDescriptionControl, $templateStepsTable, $textModulesTable, stepsChangedHandler, data);
                    }
                });
            } else {
                $templateNameControl.val("");
                $templateDescriptionControl.val("");
                stepIdCounter = 0;
                $templateStepsTable.clear();
                $templateStepsTable.rows().draw();

                $textModulesTable.clear();
                $textModulesTable.rows().draw();

                stepsChangedHandler($templateStepsTable);
            }
        });

        $('#createTemplate')
            .off('click').
            on('click', function(e) {
                createTemplate($templateNameControl, $templateDescriptionControl, $templateStepsTable, $textModulesTable);
            });
    });
}());


