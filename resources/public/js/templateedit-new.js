/*
        document.addEventListener('input', function (event) {
            if (event.target.tagName.toLowerCase() !== 'textarea') return;
            autoExpand(event.target);
        }, false);

        var autoExpand = function (field) {

            // Reset field height
            //field.style.height = 'inherit';
        
            // Get the computed styles for the element
            var computed = window.getComputedStyle(field);
        
            // Calculate the height
            var height = parseInt(computed.getPropertyValue('border-top-width'), 10)
                         + parseInt(computed.getPropertyValue('padding-top'), 10)
                         + field.scrollHeight
                         + parseInt(computed.getPropertyValue('padding-bottom'), 10)
                         + parseInt(computed.getPropertyValue('border-bottom-width'), 10);
        
            field.style.height = height + 'px';
        
        };
        //TODO: Need to change autoExpand in order to retain size of textarea once element is defocused/deselected
        //Always grows even if input is deleting lines!
        //-> Upon input, get total number of lines, adapt size as e.g. number of lines + 1
*/

//TODO: function Upon input: Get number of rows of text in all elements of class moduleTextArea -> change attribute "rows" of this element to be nrofrows+1
//Need listener for input to specific class elements -> check property for all elements of this class
//document.addEventListener('input', function (event) {
//    if (event.target.tagName.toLowerCase() !== 'textarea') return;
//    autoExpand(event.target);
//}, false);

function setupDialog(initialTemplateData) {
    var stepIdCounter = 0;
    var moduleIdCounter = 0;
    const previousTemplateData = JSON.parse(JSON.stringify(initialTemplateData));

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
    //NOTE: NEW function with only 2 arguments, stepsChangedHandler not necessary as order of textmodules is irrelevant
    function deleteTextModuleRow($table, row) {
        $table.row(row).remove().draw();
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

    function myjoin_quotes(arr) {
        return "'" + arr.join("','") + "'";
    }

    function updateTemplate($templateNameControl, $templateDescriptionControl, $templateStepsTable, $textModulesTable) {

        var formErrors = $('#templateData').validator('validate').find('.has-error');
        if (formErrors.length > 0) {
            $('html, body').animate({
                scrollTop: formErrors[0].offsetTop
            }, 300);
        }
        else{
            var $maxTMid = parseInt( $('#maxTMid').attr('value') );
            //console.log($maxTMid);
            var tmcounter = 1;
            var templateData = {
                name: $templateNameControl.val(),
                id: initialTemplateData.id, //identify newly added templatesteps as belonging to this specific template
                description: $templateDescriptionControl.val(),
                templatesteps: tableData($templateStepsTable),
                textmodules: $.map( tableData($textModulesTable), function(row) {
                    //console.log(row);
                    row.id = tmcounter;
                    //console.log(row.id);
                    row.id = row.id + $maxTMid; //Should add max id from modules db so that ids remain unique
                        //row.id starts at 1 if no previous modules are defined in this template
                        //If there already are modules in this template, increments from that number -> might yield conflicts when simply adding $maxTMid
                        //Solution: Since all modules belonging to template are deleted and re-written upon edit
                        //          Force row.id indexing to ALWAYS start at 1 and add $maxTMid as in template-create -> avoid potential id overlaps
                    //console.log(row.id);
                    tmcounter = tmcounter + 1;
                    row.step = parseInt( row.step );
                    row.template = parseInt( row.template );
                    return row;

                })

            };

            console.log( templateData ); //Shows templateData as soon as button is clicked

            var restrictedWords = new Array(":id", ":template", ":step", ":name", ":text");  //Array will be split wrongly if keywords are present in text or name of modules
            var NumberOfModules = templateData.textmodules.length;
            var errorInMod = new Set();
            var enteredKeywords = new Set();
            var error = 0; 

            for (mod = 0; mod < NumberOfModules; mod++){//loop over modules
                var modname = templateData.textmodules[mod].name;
                var modtext = templateData.textmodules[mod].text; 

                    for (var i = 0; i < restrictedWords.length; i++) {  //check all module names
                        var val = restrictedWords[i];  
                        if ((modname.toLowerCase()).indexOf(val.toString()) > -1) {  
                            error = error + 1;  
                            enteredKeywords.add(val);
                            errorInMod.add(mod+1);
                        }  
                    }  //end loop over restricted words
                    for (var i = 0; i < restrictedWords.length; i++) {  //check all module texts
                        var val = restrictedWords[i];  
                        if ((modtext.toLowerCase()).indexOf(val.toString()) > -1) {  
                            error = error + 1;  
                            enteredKeywords.add(val);
                            errorInMod.add(mod+1);
                        }  
                    }  //end loop over restricted words

            }//end loop over modules
            var printSet = Array.from(errorInMod).join(', ');
            var printKeywords = Array.from(enteredKeywords);
            printKeywords = myjoin_quotes(printKeywords);

            if (error == 0){
                $.ajax({
                    url: serverRoot + '/template/' + initialTemplateData.id,
                    type: 'PUT',
                    contentType: "application/json; charset=utf-8",
                    data: JSON.stringify( {template: templateData, oldtemplate: previousTemplateData} ),
                    success: onTemplateEditSucceeded
                });
            } else {
                var errorStr = 'You have entered some restricted keywords in module(s) ' + printSet + '. These are: ' + printKeywords + '.';
                alert(errorStr);
            }
        }
    }

    function onTemplateEditSucceeded(e) {
        window.location = serverRoot + '/template/list'
    }

    function onTemplateLoaded($templateNameControl, $templateDescriptionControl, $templateStepsTable, $textModulesTable, stepsChangedHandler, data) {
        $templateNameControl.val( data.name );
        $templateDescriptionControl.val( data.description );

        var maxId = 0;
        $.each(data.templatesteps, function( index, step ) {
            maxId = Math.max( step.id, maxId );
            //console.log(data.templatesteps[index].sequence); //sequences go from 1 to N
            data.templatesteps[index].sequence = -1;
        });
        //maxId is highest Id in templatestep db belonging to this template
        stepIdCounter = maxId + 1;
        $templateStepsTable.clear().rows.add(data.templatesteps).draw(); //Needed to render step options in tm dropdown, text and name appears anyway from db

        var maxModuleId = 0;
        $.each(data.textmodules, function( index, module ) {
            maxModuleId = Math.max( module.id, maxModuleId );
        });
        moduleIdCounter = maxModuleId + 1;
       
        function sortByKey(array, key) {
            return array.sort(function(a, b) {
                var x = a[key]; var y = b[key];
                return ((x < y) ? -1 : ((x > y) ? 1 : 0));
            });
        }

        var tm = data.textmodules;
        tm = sortByKey(tm, 'step'); //==>NOTE: textmodules are now rendered in order of step, not id once page is refreshed
        $textModulesTable.clear().rows.add(data.textmodules).draw(); //Needed to render entire textmodule table
        

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
        $textModuleTable.rows.add([{id: moduleIdCounter++, template: initialTemplateData.id, step: -1, name: "", text: ""}]).draw();
    }

    function getStepNamesToIds($templateStepsTable) {
        var namesToIds = {};

        $.each( tableData($templateStepsTable), function(index, row) {
            namesToIds[ row.type ] = index+1; //Used to be = row.id, id not needed here
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
        //selectedId contains number of step that should be selected for this module (integer)

        // delete all except choose step
        //$(dropdown).children('option').slice(2).remove(); //Only choose step AND first step (duplicate!) are left if rest of function is commented, should be only choose step!
        $(dropdown).children('option').slice(1).remove(); //Only choose step left if slice(1) is chosen
        
        // add an option for each step name
        $.each( stepNameOptions(stepNamesToIds), function(index, option) {
            dropdown.options.add( option );
        });

        // reset selected option, if still present
        $.each( $(dropdown).children('option'), function(index, option) {
            if( $(option).val() == selectedId )
                $(option).prop('selected', true);
        });
        

    }//end updateDropdown
    

    function onStepsChanged($textModulesTableDOM, $templateStepsTable) {
        var stepNamesToIds = getStepNamesToIds($templateStepsTable);

        $textModulesTableDOM.find("select").each(function(dontcare, dropdown) {
            updateDropdown( dropdown, stepNamesToIds );
        });
    }

    function renderModuleStep($templateStepsTable, data, type, row, meta) {
      //NOTE: This function is called as many times as there are modules once the page is first loaded, called again when step selection is changed which fixes duplication
      //NOTE: Reads step to choose in dropdown menu from $templateStepsTable, id from textmodule table needs to be associated with templateStepsTable!
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
        });//end of .each
        return $select.prop("outerHTML");
    }//end renderModuleStep

    function renderModuleName(data, type, row, meta) {
        var $input = $("<input>",{
                    "value": data,
                    "type": "text",
                    "class": "form-control data-input",
                    "width": "100%",
                    "data-attribute": "name"});
        return $input.prop("outerHTML");
    }


function autoSize(ele)
{
   ele.style.height = 'auto';
   var newHeight = (ele.scrollHeight > 32 ? ele.scrollHeight : 32);
   ele.style.height = newHeight.toString() + 'px';
}

    //Assigns attributes to textareas inside table with id="textModulesTable"
    function renderModuleText(data, type, row, meta) {
        var $textarea = $("<textarea>", {
            "text": data,
            "type": "text",
            "class": "form-control data-input moduleTextArea", //added specific new class moduleTextArea
             "rows": 4, //Used to be 1, can also be used to change size of textarea
             "width": "100%",
             "height": "auto", //accurately changes size of textarea
             "data-attribute": "text"});
        return $textarea.prop("outerHTML");
    }

    function onTextModuleRowCreated($textModulesTable, row, data) {
        $(row).off('change', ".data-input").on('change', ".data-input", function() { moduleTextBinding($textModulesTable, $(this), row); });
        $(row).off('click', '.btn-delete-row').on('click', '.btn-delete-row', function() { deleteTextModuleRow($textModulesTable, row); });
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
        //NOTE: This function is called once textmodule delete button has been clicked

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

        onTemplateLoaded($templateNameControl, $templateDescriptionControl, $templateStepsTable, $textModulesTable, stepsChangedHandler, initialTemplateData);

        $('#updateTemplate')
            .off('click').
            on('click', function(e) {
                console.log("#updateTemplate called from .js");
                updateTemplate($templateNameControl, $templateDescriptionControl, $templateStepsTable, $textModulesTable);
            });
    });
}
