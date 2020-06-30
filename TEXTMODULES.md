# TODOs for text modules

1. `traqbio.actions.template/update-template` 
   Add update implementation for text modules similar to project steps.
 
2. When a project is created from a template, add the text module association to the DB table `:textmoduleprojectstep`. 
   
3. Integrate text modules from read template into select boxes (display names for selection) for the corresponding project steps.
   Implement on change handlers that fill in the text of the corresponding text module.
   `projectcreate.html`, `projectedit.html`, `createProject.js` and `editProject.js`.